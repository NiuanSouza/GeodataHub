package com.bifrostconnect.api_geo.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.bifrostconnect.api_geo.dto.ResultadoValidacaoTopologica.Problema;
import com.bifrostconnect.api_geo.dto.ResultadoValidacaoTopologica.TipoProblema;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class FeicaoCargaService {

    private static final int LOTE_MAX_FEICOES = 500;

    // o codigo_imovel lá no banco é VARCHAR(100), então corta se passar disso
    private static final int TAMANHO_MAX_CODIGO = 100;

    // nomes de propriedade que a gente aceita como código do imóvel ou feição geoespacial (em ordem de prioridade)
    private static final List<String> CHAVES_CODIGO_IMOVEL = List.of(
            "cod_imovel", "codigo_imovel", "id_propriedade", "cod_car", "car",
            "cd_mun", "terrai_cod", "cnuc", "codigo_uc", "código uc",
            "cd_sipra", "cd_quilomb", "seq_tad", "num_tad", "codigo",
            "cod_bioma", "cd_bioma", "id_uc", "uuid", "fid", "gid");

    // o ST_Force2D tá aí porque a coluna é 2D e o banco recusa coordenada com Z
    private static final String SQL_INSERIR = """
            INSERT INTO feicao_carga (arquivo_processado_id, codigo_imovel, geom, atributos)
            VALUES (?, ?, ST_Force2D(ST_SetSRID(ST_GeomFromGeoJSON(?), %s)), ?::jsonb)
            """.formatted(PadronizacaoEpsgService.EPSG_OFICIAL);

    private final ObjectMapper mapper = new ObjectMapper();
    private final JdbcTemplate jdbcTemplate;

    public FeicaoCargaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // rejeitadas = feições que nem entraram na feicao_carga e já viraram problema
    public record ResultadoCarga(int totalFeicoes, int carregadas, List<Problema> rejeitadas) {}

    public ResultadoCarga carregar(Long arquivoProcessadoId, Path copiaTrabalho) throws IOException {
        List<Problema> rejeitadas = new ArrayList<>();
        Set<String> codigosVistos = new HashSet<>();
        List<Object[]> lote = new ArrayList<>();
        int total = 0;
        int carregadas = 0;

        try (JsonParser parser = mapper.getFactory().createParser(copiaTrabalho.toFile())) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("GeoJSON inválido: esperado um FeatureCollection.");
            }

            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String campo = parser.currentName();
                JsonToken valor = parser.nextToken();

                if (!"features".equals(campo) || valor != JsonToken.START_ARRAY) {
                    parser.skipChildren();
                    continue;
                }

                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    JsonNode feicao = mapper.readTree(parser);
                    total++;

                    String codigo = extrairCodigoImovel(feicao, total);
                    String motivo = verificarGeometria(feicao.get("geometry"));

                    if (motivo != null) {
                        rejeitadas.add(Problema.daFeicao(TipoProblema.GEOMETRIA_INVALIDA, codigo, motivo));
                        continue;
                    }
                    if (!codigosVistos.add(codigo)) {
                        rejeitadas.add(Problema.daFeicao(TipoProblema.CODIGO_IMOVEL_DUPLICADO, codigo,
                                "Código de imóvel " + codigo + " repetido no arquivo; só a primeira ocorrência foi carregada."));
                        continue;
                    }

                    JsonNode propriedades = feicao.get("properties");
                    lote.add(new Object[] {
                            arquivoProcessadoId,
                            codigo,
                            feicao.get("geometry").toString(),
                            propriedades != null && propriedades.isObject() ? propriedades.toString() : null
                    });

                    if (lote.size() >= LOTE_MAX_FEICOES) {
                        carregadas += descarregarLote(lote);
                    }
                }
            }
        }
        carregadas += descarregarLote(lote);

        return new ResultadoCarga(total, carregadas, rejeitadas);
    }


    static String verificarGeometria(JsonNode geometria) {
        if (geometria == null || !geometria.isObject()) {
            return "Feição sem geometria.";
        }
        String tipo = geometria.path("type").asText("");
        JsonNode coordenadas = geometria.get("coordinates");

        switch (tipo) {
            case "Polygon":
                return verificarPoligono(coordenadas, "");
            case "MultiPolygon":
                if (coordenadas == null || !coordenadas.isArray() || coordenadas.isEmpty()) {
                    return "MultiPolygon sem polígonos.";
                }
                for (int i = 0; i < coordenadas.size(); i++) {
                    String motivo = verificarPoligono(coordenadas.get(i), " (parte " + (i + 1) + ")");
                    if (motivo != null) {
                        return motivo;
                    }
                }
                return null;
            default:
                return "Geometria do tipo '" + tipo + "' não é um polígono (geometria aberta ou sem área).";
        }
    }

    private static String verificarPoligono(JsonNode aneis, String parte) {
        if (aneis == null || !aneis.isArray() || aneis.isEmpty()) {
            return "Polígono sem anéis" + parte + ".";
        }
        for (int i = 0; i < aneis.size(); i++) {
            JsonNode anel = aneis.get(i);
            String nomeAnel = (i == 0 ? "Anel externo" : "Anel interno " + i) + parte;

            if (anel == null || !anel.isArray() || anel.size() < 4) {
                return nomeAnel + " tem menos de 4 pontos.";
            }
            for (JsonNode ponto : anel) {
                String motivo = verificarPonto(ponto);
                if (motivo != null) {
                    return nomeAnel + ": " + motivo;
                }
            }
            JsonNode primeiro = anel.get(0);
            JsonNode ultimo = anel.get(anel.size() - 1);
            if (primeiro.get(0).asDouble() != ultimo.get(0).asDouble()
                    || primeiro.get(1).asDouble() != ultimo.get(1).asDouble()) {
                return nomeAnel + " não está fechado (primeiro ponto diferente do último).";
            }
        }
        return null;
    }

    // a cópia já tá em SIRGAS 2000 (graus), então dá pra barrar lon/lat fora da faixa
    private static String verificarPonto(JsonNode ponto) {
        if (ponto == null || !ponto.isArray() || ponto.size() < 2
                || !ponto.get(0).isNumber() || !ponto.get(1).isNumber()) {
            return "coordenada malformada.";
        }
        double lon = ponto.get(0).asDouble();
        double lat = ponto.get(1).asDouble();
        if (!Double.isFinite(lon) || !Double.isFinite(lat)
                || lon < -180 || lon > 180 || lat < -90 || lat > 90) {
            return "coordenada fora da faixa do SIRGAS 2000 (" + lon + ", " + lat + ").";
        }
        return null;
    }

    static String extrairCodigoImovel(JsonNode feicao, int posicao) {
        JsonNode propriedades = feicao.get("properties");
        if (propriedades != null && propriedades.isObject()) {
            for (String chavePrioritaria : CHAVES_CODIGO_IMOVEL) {
                Iterator<String> campos = propriedades.fieldNames();
                while (campos.hasNext()) {
                    String campo = campos.next();
                    if (chavePrioritaria.equals(campo.toLowerCase(Locale.ROOT))) {
                        String valor = propriedades.get(campo).asText("").trim();
                        if (!valor.isEmpty()) {
                            return valor.length() > TAMANHO_MAX_CODIGO ? valor.substring(0, TAMANHO_MAX_CODIGO) : valor;
                        }
                    }
                }
            }
        }
        // se o arquivo não trouxe código, usa a posição da feição pra pelo menos dar pra achar o polígono
        return "FEICAO_" + posicao;
    }

    private int descarregarLote(List<Object[]> lote) {
        if (lote.isEmpty()) {
            return 0;
        }
        // Tarefa 15: o ST_GeomFromGeoJSON/ST_SetSRID pode recusar a geometria aqui
        ExecucaoGeoespacial.executar(jdbcTemplate, "gravação das geometrias da carga",
                () -> jdbcTemplate.batchUpdate(SQL_INSERIR, lote));
        int quantidade = lote.size();
        lote.clear();
        return quantidade;
    }
}
