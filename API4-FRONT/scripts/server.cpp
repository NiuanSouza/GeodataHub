#include <iostream>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>
#include <map>
#include <unordered_map>
#include <thread>
#include <atomic>
#include <chrono>
#include <cstring>
#include <cctype>
#include <cstdlib>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <signal.h>
#include <libgen.h>

#include "dist_embedded.hpp"

static std::atomic<bool> g_running(true);
static int g_server_fd = -1;

void handle_signal(int sig) {
    (void)sig;
    g_running = false;
    if (g_server_fd >= 0) {
        close(g_server_fd);
        g_server_fd = -1;
    }
}

static inline std::string trim(const std::string& str) {
    size_t first = 0;
    while (first < str.size() && (std::isspace(static_cast<unsigned char>(str[first])) || str[first] == '\r')) {
        first++;
    }
    if (first == str.size()) return "";
    size_t last = str.size() - 1;
    while (last > first && (std::isspace(static_cast<unsigned char>(str[last])) || str[last] == '\r')) {
        last--;
    }
    return str.substr(first, last - first + 1);
}

static inline std::string strip_quotes(const std::string& val) {
    if (val.size() >= 2) {
        if ((val.front() == '"' && val.back() == '"') || (val.front() == '\'' && val.back() == '\'')) {
            return val.substr(1, val.size() - 2);
        }
    }
    return val;
}

static std::string escape_json(const std::string& s) {
    std::ostringstream o;
    for (char c : s) {
        if (c == '"') o << "\\\"";
        else if (c == '\\') o << "\\\\";
        else if (c == '\b') o << "\\b";
        else if (c == '\f') o << "\\f";
        else if (c == '\n') o << "\\n";
        else if (c == '\r') o << "\\r";
        else if (c == '\t') o << "\\t";
        else if ('\x00' <= c && c <= '\x1f') {
            o << "\\u" << std::hex << (int)c;
        } else {
            o << c;
        }
    }
    return o.str();
}

static bool file_exists(const std::string& path) {
    struct stat st{};
    return (stat(path.c_str(), &st) == 0 && S_ISREG(st.st_mode));
}

static std::string get_executable_dir() {
    char buf[1024] = {0};
    ssize_t len = readlink("/proc/self/exe", buf, sizeof(buf) - 1);
    if (len > 0) {
        buf[len] = '\0';
        char* dir = dirname(buf);
        if (dir) return std::string(dir);
    }
    return ".";
}

static bool load_dotenv(const std::string& path, std::map<std::string, std::string>& env_map) {
    std::ifstream file(path);
    if (!file.is_open()) return false;

    std::string line;
    while (std::getline(file, line)) {
        line = trim(line);
        if (line.empty() || line[0] == '#') continue;

        if (line.rfind("export ", 0) == 0) {
            line = trim(line.substr(7));
        }

        size_t eq_pos = line.find('=');
        if (eq_pos == std::string::npos) continue;

        std::string key = trim(line.substr(0, eq_pos));
        std::string val = trim(line.substr(eq_pos + 1));
        val = strip_quotes(val);

        if (!key.empty()) {
            env_map[key] = val;
        }
    }
    return true;
}

static std::string url_decode(const std::string& in) {
    std::string out;
    out.reserve(in.size());
    for (size_t i = 0; i < in.size(); ++i) {
        if (in[i] == '%' && i + 2 < in.size()) {
            int value = 0;
            std::istringstream is(in.substr(i + 1, 2));
            if (is >> std::hex >> value) {
                out += static_cast<char>(value);
                i += 2;
            } else {
                out += in[i];
            }
        } else if (in[i] == '+') {
            out += ' ';
        } else {
            out += in[i];
        }
    }
    return out;
}

static std::string get_mime_from_ext(const std::string& path) {
    size_t dot = path.rfind('.');
    if (dot == std::string::npos) return "application/octet-stream";
    std::string ext = path.substr(dot);
    for (auto& c : ext) c = std::tolower(static_cast<unsigned char>(c));

    if (ext == ".html" || ext == ".htm") return "text/html; charset=utf-8";
    if (ext == ".js" || ext == ".mjs") return "application/javascript; charset=utf-8";
    if (ext == ".css") return "text/css; charset=utf-8";
    if (ext == ".json") return "application/json; charset=utf-8";
    if (ext == ".png") return "image/png";
    if (ext == ".jpg" || ext == ".jpeg") return "image/jpeg";
    if (ext == ".gif") return "image/gif";
    if (ext == ".svg") return "image/svg+xml";
    if (ext == ".ico") return "image/x-icon";
    if (ext == ".woff2") return "font/woff2";
    if (ext == ".woff") return "font/woff";
    if (ext == ".ttf") return "font/ttf";
    if (ext == ".txt") return "text/plain; charset=utf-8";
    return "application/octet-stream";
}

struct AppConfig {
    int port = 3000;
    std::string host = "0.0.0.0";
    std::string loaded_env_path = "";
    std::map<std::string, std::string> env_vars;
    std::string env_json = "{}";
    std::string injected_index_html;
    std::string custom_dist_dir = "";
};

static void prepare_injected_html(AppConfig& config) {
    std::ostringstream json;
    json << "{";
    bool first = true;
    for (const auto& kv : config.env_vars) {
        if (kv.first.rfind("VITE_", 0) == 0 || kv.first == "API_BASE_URL" || kv.first == "PORT") {
            if (!first) json << ",";
            first = false;
            json << "\"" << escape_json(kv.first) << "\":\"" << escape_json(kv.second) << "\"";
        }
    }
    json << "}";
    config.env_json = json.str();

    // Encontra o index.html embutido
    std::string raw_html;
    for (size_t i = 0; i < EMBEDDED_ASSETS_COUNT; ++i) {
        if (std::string(EMBEDDED_ASSETS[i].path) == "/index.html") {
            raw_html.assign(reinterpret_cast<const char*>(EMBEDDED_ASSETS[i].data), EMBEDDED_ASSETS[i].size);
            break;
        }
    }

    if (raw_html.empty()) {
        raw_html = "<!doctype html><html><head><title>GeodataHub</title></head><body><div id=\"app\"></div></body></html>";
    }

    std::string script_tag = "<script id=\"__ENV_CONFIG__\">\n  window.__ENV__ = " + config.env_json + ";\n</script>";

    // Substitui o placeholder se existir
    const std::string placeholder = "<script>\n      window.__ENV__ = window.__ENV__ || {};\n    </script>";
    const std::string placeholder2 = "<script>window.__ENV__ = window.__ENV__ || {};</script>";
    size_t pos = raw_html.find(placeholder);
    if (pos != std::string::npos) {
        config.injected_index_html = raw_html.substr(0, pos) + script_tag + raw_html.substr(pos + placeholder.size());
    } else if ((pos = raw_html.find(placeholder2)) != std::string::npos) {
        config.injected_index_html = raw_html.substr(0, pos) + script_tag + raw_html.substr(pos + placeholder2.size());
    } else {
        // Insere logo após <head>
        pos = raw_html.find("<head>");
        if (pos != std::string::npos) {
            config.injected_index_html = raw_html.substr(0, pos + 6) + "\n    " + script_tag + raw_html.substr(pos + 6);
        } else {
            config.injected_index_html = script_tag + raw_html;
        }
    }
}

static bool send_all(int fd, const char* data, size_t total) {
    size_t sent = 0;
    while (sent < total) {
        ssize_t n = write(fd, data + sent, total - sent);
        if (n <= 0) return false;
        sent += n;
    }
    return true;
}

static void send_response(int fd, int status, const std::string& status_text,
                          const std::string& mime, const char* body, size_t body_len,
                          bool is_immutable = false) {
    std::ostringstream hdr;
    hdr << "HTTP/1.1 " << status << " " << status_text << "\r\n";
    hdr << "Content-Type: " << mime << "\r\n";
    hdr << "Content-Length: " << body_len << "\r\n";
    hdr << "Access-Control-Allow-Origin: *\r\n";
    hdr << "Connection: close\r\n";
    if (is_immutable) {
        hdr << "Cache-Control: public, max-age=31536000, immutable\r\n";
    } else {
        hdr << "Cache-Control: no-cache, no-store, must-revalidate\r\n";
    }
    hdr << "\r\n";

    std::string header_str = hdr.str();
    send_all(fd, header_str.data(), header_str.size());
    if (body_len > 0 && body != nullptr) {
        send_all(fd, body, body_len);
    }
}

static void handle_request(int client_fd, const AppConfig& config) {
    char buffer[4096] = {0};
    ssize_t bytes = read(client_fd, buffer, sizeof(buffer) - 1);
    if (bytes <= 0) {
        close(client_fd);
        return;
    }

    std::string req(buffer, bytes);
    size_t first_line_end = req.find("\r\n");
    if (first_line_end == std::string::npos) first_line_end = req.find("\n");
    if (first_line_end == std::string::npos) {
        close(client_fd);
        return;
    }

    std::string first_line = req.substr(0, first_line_end);
    std::istringstream iss(first_line);
    std::string method, raw_path, proto;
    if (!(iss >> method >> raw_path >> proto)) {
        close(client_fd);
        return;
    }

    if (method != "GET" && method != "HEAD") {
        std::string msg = "Method Not Allowed";
        send_response(client_fd, 405, "Method Not Allowed", "text/plain", msg.data(), msg.size());
        close(client_fd);
        return;
    }

    // Limpa query string e decodifica URL
    size_t qpos = raw_path.find('?');
    std::string path = (qpos != std::string::npos) ? raw_path.substr(0, qpos) : raw_path;
    path = url_decode(path);

    // Evita directory traversal
    if (path.find("..") != std::string::npos) {
        std::string msg = "Forbidden";
        send_response(client_fd, 403, "Forbidden", "text/plain", msg.data(), msg.size());
        close(client_fd);
        return;
    }

    if (path.empty() || path == "/") {
        path = "/index.html";
    }

    // Endpoint de saúde
    if (path == "/health" || path == "/api/health") {
        std::string body = "{\"status\":\"UP\"}";
        send_response(client_fd, 200, "OK", "application/json", body.data(), body.size());
        close(client_fd);
        return;
    }

    // Endpoint /env.js dinâmico
    if (path == "/env.js") {
        std::string body = "window.__ENV__ = " + config.env_json + ";\n";
        send_response(client_fd, 200, "OK", "application/javascript; charset=utf-8", body.data(), body.size());
        close(client_fd);
        return;
    }

    // Se o pedido for index.html, serve a versão com as variáveis injetadas
    if (path == "/index.html") {
        send_response(client_fd, 200, "OK", "text/html; charset=utf-8",
                      config.injected_index_html.data(), config.injected_index_html.size(), false);
        close(client_fd);
        return;
    }

    // 1. Verifica se existe arquivo no diretório dist customizado em disco
    if (!config.custom_dist_dir.empty()) {
        std::string disk_path = config.custom_dist_dir + path;
        if (file_exists(disk_path)) {
            std::ifstream file(disk_path, std::ios::binary);
            if (file.is_open()) {
                std::string content((std::istreambuf_iterator<char>(file)), std::istreambuf_iterator<char>());
                std::string mime = get_mime_from_ext(disk_path);
                bool immutable = (path.rfind("/assets/", 0) == 0);
                send_response(client_fd, 200, "OK", mime, content.data(), content.size(), immutable);
                close(client_fd);
                return;
            }
        }
    }

    // 2. Busca nos assets embutidos
    for (size_t i = 0; i < EMBEDDED_ASSETS_COUNT; ++i) {
        if (std::string(EMBEDDED_ASSETS[i].path) == path) {
            bool immutable = (path.rfind("/assets/", 0) == 0);
            send_response(client_fd, 200, "OK", EMBEDDED_ASSETS[i].mime,
                          reinterpret_cast<const char*>(EMBEDDED_ASSETS[i].data),
                          EMBEDDED_ASSETS[i].size, immutable);
            close(client_fd);
            return;
        }
    }

    // 3. Roteamento SPA: Se não tiver extensão, entrega o index.html (modo History do Vue Router)
    size_t last_dot = path.rfind('.');
    size_t last_slash = path.rfind('/');
    bool has_extension = (last_dot != std::string::npos && (last_slash == std::string::npos || last_dot > last_slash));

    if (!has_extension) {
        send_response(client_fd, 200, "OK", "text/html; charset=utf-8",
                      config.injected_index_html.data(), config.injected_index_html.size(), false);
        close(client_fd);
        return;
    }

    // 4. Arquivo estático não encontrado
    std::string not_found = "404 Not Found: " + path;
    send_response(client_fd, 404, "Not Found", "text/plain", not_found.data(), not_found.size());
    close(client_fd);
}

static void print_usage(const char* prog) {
    std::cout << "GeodataHub - Frontend Web Server Executavel\n\n"
              << "Uso:\n"
              << "  " << prog << " [opcoes]\n\n"
              << "Opcoes:\n"
              << "  -p, --port <porta>    Porta HTTP do servidor (padrao: 3000 ou PORT do .env)\n"
              << "  -h, --host <ip>       Interface de rede para escuta (padrao: 0.0.0.0)\n"
              << "  -e, --env <arquivo>   Caminho para o arquivo .env customizado\n"
              << "  -d, --dist <pasta>    Pasta dist alternativa com arquivos estaticos\n"
              << "  -v, --version         Exibe versao da aplicacao\n"
              << "  --help                Exibe esta ajuda\n\n"
              << "Variaveis de ambiente aceitas no .env:\n"
              << "  PORT                  Porta do servidor (ex: 3000)\n"
              << "  HOST                  Host de escuta (ex: 0.0.0.0)\n"
              << "  VITE_API_BASE_URL     URL da API Backend (ex: http://localhost:8080)\n"
              << "  VITE_*                Qualquer variavel iniciada em VITE_\n\n";
}

int main(int argc, char** argv) {
    AppConfig config;
    std::string custom_env_path = "";
    bool custom_port_specified = false;

    // Processa argumentos de linha de comando
    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "--help") {
            print_usage(argv[0]);
            return 0;
        } else if (arg == "-v" || arg == "--version") {
            std::cout << "GeodataHub Frontend v1.0.0 (Linux x86_64 Standalone)\n";
            return 0;
        } else if ((arg == "-p" || arg == "--port") && i + 1 < argc) {
            config.port = std::atoi(argv[++i]);
            custom_port_specified = true;
        } else if ((arg == "-h" || arg == "--host") && i + 1 < argc) {
            config.host = argv[++i];
        } else if ((arg == "-e" || arg == "--env") && i + 1 < argc) {
            custom_env_path = argv[++i];
        } else if ((arg == "-d" || arg == "--dist") && i + 1 < argc) {
            config.custom_dist_dir = argv[++i];
        }
    }

    std::string exe_dir = get_executable_dir();

    // Ordem de busca do .env:
    // 1. Argumento --env
    // 2. ./.env (diretorio atual de execucao)
    // 3. <exe_dir>/.env (ao lado do binario)
    // 4. <exe_dir>/../.env
    std::vector<std::string> search_paths;
    if (!custom_env_path.empty()) {
        search_paths.push_back(custom_env_path);
    }
    search_paths.push_back(".env");
    search_paths.push_back(exe_dir + "/.env");
    search_paths.push_back(exe_dir + "/../.env");

    for (const auto& p : search_paths) {
        if (file_exists(p)) {
            if (load_dotenv(p, config.env_vars)) {
                config.loaded_env_path = p;
                break;
            }
        }
    }

    // Le variaveis de ambiente do sistema como fallback/override
    if (const char* env_port = std::getenv("PORT")) {
        if (!custom_port_specified) config.port = std::atoi(env_port);
    } else if (!custom_port_specified && config.env_vars.find("PORT") != config.env_vars.end()) {
        config.port = std::atoi(config.env_vars["PORT"].c_str());
    }

    if (const char* env_host = std::getenv("HOST")) {
        config.host = env_host;
    } else if (config.env_vars.find("HOST") != config.env_vars.end()) {
        config.host = config.env_vars["HOST"];
    }

    // Fallback para VITE_API_BASE_URL
    if (config.env_vars.find("VITE_API_BASE_URL") == config.env_vars.end()) {
        if (const char* sys_api = std::getenv("VITE_API_BASE_URL")) {
            config.env_vars["VITE_API_BASE_URL"] = sys_api;
        } else if (config.env_vars.find("API_BASE_URL") != config.env_vars.end()) {
            config.env_vars["VITE_API_BASE_URL"] = config.env_vars["API_BASE_URL"];
        } else {
            config.env_vars["VITE_API_BASE_URL"] = "http://localhost:8080";
        }
    }

    prepare_injected_html(config);

    // Registra tratadores de sinal para encerramento gracioso
    signal(SIGINT, handle_signal);
    signal(SIGTERM, handle_signal);

    int server_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (server_fd < 0) {
        std::cerr << "[ERRO] Nao foi possivel criar socket.\n";
        return 1;
    }
    g_server_fd = server_fd;

    int opt = 1;
    setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
#ifdef SO_REUSEPORT
    setsockopt(server_fd, SOL_SOCKET, SO_REUSEPORT, &opt, sizeof(opt));
#endif

    sockaddr_in address{};
    address.sin_family = AF_INET;
    if (config.host == "0.0.0.0" || config.host.empty()) {
        address.sin_addr.s_addr = INADDR_ANY;
    } else {
        inet_pton(AF_INET, config.host.c_str(), &address.sin_addr);
    }
    address.sin_port = htons(config.port);

    if (bind(server_fd, (struct sockaddr*)&address, sizeof(address)) < 0) {
        std::cerr << "[ERRO] Falha ao vincular porta " << config.port << ". A porta pode estar em uso.\n";
        close(server_fd);
        return 1;
    }

    if (listen(server_fd, 64) < 0) {
        std::cerr << "[ERRO] Falha ao escutar conexoes.\n";
        close(server_fd);
        return 1;
    }

    std::cout << "\n=======================================================\n"
              << "       GeodataHub - Frontend Web Server Standalone     \n"
              << "=======================================================\n"
              << "  Status:    Em execucao\n"
              << "  URL Local: http://localhost:" << config.port << "\n"
              << "  Host:      " << config.host << ":" << config.port << "\n"
              << "  API Base:  " << config.env_vars["VITE_API_BASE_URL"] << "/api\n";

    if (!config.loaded_env_path.empty()) {
        std::cout << "  .env:      " << config.loaded_env_path << " (" << config.env_vars.size() << " variaveis lidas)\n";
    } else {
        std::cout << "  .env:      Nenhum .env detectado (usando valores padrao)\n";
    }

    std::cout << "=======================================================\n"
              << "Pressione Ctrl+C para encerrar o servidor.\n\n" << std::flush;

    while (g_running) {
        sockaddr_in client_addr{};
        socklen_t client_len = sizeof(client_addr);
        int client_fd = accept(server_fd, (struct sockaddr*)&client_addr, &client_len);
        if (client_fd < 0) {
            if (!g_running) break;
            continue;
        }

        std::thread([client_fd, config]() {
            handle_request(client_fd, config);
        }).detach();
    }

    std::cout << "\n[INFO] Servidor finalizado com sucesso.\n";
    if (g_server_fd >= 0) {
        close(g_server_fd);
        g_server_fd = -1;
    }

    return 0;
}
