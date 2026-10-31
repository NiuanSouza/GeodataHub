package com.bifrostconnect.api_geo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processador de ambiente do Spring Boot que busca e carrega automaticamente
 * variáveis de arquivos .env para a configuração da aplicação.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        File envFile = resolveEnvFile();
        if (envFile == null || !envFile.exists() || !envFile.isFile()) {
            log.info("[Dotenv] Nenhum arquivo .env encontrado nas localizações padrão. Utilizando variáveis de sistema e application.properties.");
            return;
        }

        log.info("[Dotenv] Carregando variáveis de ambiente do arquivo: {}", envFile.getAbsolutePath());
        Map<String, Object> envMap = loadEnvFile(envFile);

        if (!envMap.isEmpty()) {
            MutablePropertySources propertySources = environment.getPropertySources();
            if (propertySources.contains(CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME)) {
                propertySources.addAfter(CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME,
                        new MapPropertySource("dotenvProperties", envMap));
            } else {
                propertySources.addFirst(new MapPropertySource("dotenvProperties", envMap));
            }

            // Garante que propriedades do sistema também reflitam os valores
            for (Map.Entry<String, Object> entry : envMap.entrySet()) {
                if (System.getProperty(entry.getKey()) == null && entry.getValue() != null) {
                    System.setProperty(entry.getKey(), entry.getValue().toString());
                }
            }
        }
    }

    private File resolveEnvFile() {
        List<Path> candidatePaths = new ArrayList<>();

        // 1. Caminho customizado via propriedade de sistema ou variável de ambiente
        String customPath = System.getProperty("dotenv.file");
        if (customPath == null || customPath.isBlank()) {
            customPath = System.getenv("DOTENV_FILE");
        }
        if (customPath != null && !customPath.isBlank()) {
            candidatePaths.add(Paths.get(customPath));
        }

        // 2. Diretório de trabalho atual
        candidatePaths.add(Paths.get(".env"));
        candidatePaths.add(Paths.get("executavel", ".env"));

        // 3. Diretório onde o JAR / classes estão localizados
        try {
            URI codeSourceUri = DotenvEnvironmentPostProcessor.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI();
            File codeSourceFile = new File(codeSourceUri);
            File dir = codeSourceFile.isDirectory() ? codeSourceFile : codeSourceFile.getParentFile();
            if (dir != null) {
                candidatePaths.add(dir.toPath().resolve(".env"));
            }
        } catch (Exception ignored) {
        }

        // 4. Diretório pai (caso executado a partir de uma subpasta)
        candidatePaths.add(Paths.get("..", ".env"));

        for (Path p : candidatePaths) {
            try {
                File f = p.toFile();
                if (f.exists() && f.isFile()) {
                    return f.getAbsoluteFile();
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    public static Map<String, Object> loadEnvFile(File file) {
        Map<String, Object> map = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx == -1) {
                    continue;
                }
                String key = line.substring(0, eqIdx).trim();
                String value = line.substring(eqIdx + 1).trim();

                // Trata aspas ao redor do valor
                if ((value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) ||
                    (value.startsWith("'") && value.endsWith("'") && value.length() >= 2)) {
                    value = value.substring(1, value.length() - 1);
                    value = value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"");
                } else {
                    int commentIdx = value.indexOf(" #");
                    if (commentIdx != -1) {
                        value = value.substring(0, commentIdx).trim();
                    }
                }

                if (!key.isEmpty()) {
                    map.put(key, value);

                    // Mapeamentos de conveniência
                    if ("PORT".equalsIgnoreCase(key) && !map.containsKey("server.port")) {
                        map.put("server.port", value);
                    }
                }
            }

            // Constrói URL JDBC automaticamente caso DB_HOST e DB_NAME sejam informados
            if (!map.containsKey("spring.datasource.url") && !map.containsKey("SPRING_DATASOURCE_URL")) {
                Object dbHost = map.get("DB_HOST");
                Object dbPort = map.getOrDefault("DB_PORT", "5432");
                Object dbName = map.get("DB_NAME");
                if (dbHost != null && dbName != null) {
                    String url = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
                    map.put("spring.datasource.url", url);
                    map.put("SPRING_DATASOURCE_URL", url);
                }
            }

            if (!map.containsKey("spring.datasource.username") && !map.containsKey("SPRING_DATASOURCE_USERNAME")) {
                Object dbUser = map.get("DB_USER");
                if (dbUser == null) dbUser = map.get("DB_USERNAME");
                if (dbUser != null) {
                    map.put("spring.datasource.username", dbUser);
                    map.put("SPRING_DATASOURCE_USERNAME", dbUser);
                }
            }

            if (!map.containsKey("spring.datasource.password") && !map.containsKey("SPRING_DATASOURCE_PASSWORD")) {
                Object dbPass = map.get("DB_PASSWORD");
                if (dbPass != null) {
                    map.put("spring.datasource.password", dbPass);
                    map.put("SPRING_DATASOURCE_PASSWORD", dbPass);
                }
            }

        } catch (Exception e) {
            log.error("[Dotenv] Erro ao ler arquivo .env: {}", e.getMessage(), e);
        }
        return map;
    }
}
