package com.bifrostconnect.api_geo.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Serviço para importação em lote dos datasets da pasta GEOESPACIAIS
 * diretamente na tabela camada_referencia via INSERT em streaming.
 */
@Service
public class CargaCamadaReferenciaService {

    private static final int LOTE_MAX = 500;

    private static final String SQL_INSERIR_REFERENCIA = """
            INSERT INTO camada_referencia (fonte, nome_camada, identificador_origem, geom, atributos, data_referencia)
            VALUES (?, ?, ?, ST_Force2D(ST_SetSRID(ST_GeomFromGeoJSON(?), 4674)), ?::jsonb, CURRENT_DATE)
            """;

    public record DefinicaoFonteReferencia(String fonte, String nomeCamada, String caminhoRelativo) {}

    public static final List<DefinicaoFonteReferencia> FONTES_GEOESPACIAIS = List.of(
            new DefinicaoFonteReferencia("FUNAI_TERRA_INDIGENA", "Terras Indígenas (FUNAI)",
                    "FUNAI (Terras Indigenas)/tis_poligonais.zip"),
            new DefinicaoFonteReferencia("IBAMA_EMBARGO", "Áreas Embargadas (IBAMA)",
                    "IBAMA (Áreas Embargadas)/adm_embargos_ibama_a(PARANA).zip"),
            new DefinicaoFonteReferencia("IBGE_BIOMA_250MIL", "Biomas 1:250.000 (IBGE)",
                    "IBGE (Limite de Biomas_ Malha Municipal)/Biomas_250mil.zip"),
            new DefinicaoFonteReferencia("IBGE_BIOMA_5000MIL", "Biomas 1:5.000.000 (IBGE)",
                    "IBGE (Limite de Biomas_ Malha Municipal)/Biomas_5000mil.zip"),
            new DefinicaoFonteReferencia("IBGE_MUNICIPIO", "Malha Municipal PR 2025 (IBGE)",
                    "IBGE (Limite de Biomas_ Malha Municipal)/PR_Municipios_2025.zip"),
            new DefinicaoFonteReferencia("ICMBIO_UC_FEDERAL", "Unidades de Conservação Federais (ICMBio)",
                    "ICMBio (Unidades De Conservação Federais)/copy_of_limite_ucs_federais_082026.zip"),
            new DefinicaoFonteReferencia("INCAR_AREA_IMOVEL", "Área do Imóvel Rural (SICAR/INCAR)",
                    "INCAR/AREA_IMOVEL.zip"),
            new DefinicaoFonteReferencia("INCRA_ASSENTAMENTO", "Assentamentos Brasil (INCRA)",
                    "INCRA (Assentamentos_ Terras quilombolas)/Assentamento Brasil.zip"),
            new DefinicaoFonteReferencia("INCRA_QUILOMBOLA", "Áreas de Quilombolas (INCRA)",
                    "INCRA (Assentamentos_ Terras quilombolas)/Áreas de Quilombolas.zip"),
            new DefinicaoFonteReferencia("INPE_QUEIMADA", "Focos de Queimadas BDQueimadas (INPE)",
                    "INPE (Queimadas_ Desmatamento)/bdqueimadas_2025-09-01_2026-09-02.zip"),
            new DefinicaoFonteReferencia("INPE_DESMATAMENTO_CERRADO", "Desmatamento Anual Cerrado PRODES (INPE)",
                    "INPE (Queimadas_ Desmatamento)/yearly_deforestation_biome_cerrado_v20260717.zip"),
            new DefinicaoFonteReferencia("INPE_DESMATAMENTO_MATA_ATLANTICA", "Desmatamento Anual Mata Atlântica PRODES (INPE)",
                    "INPE (Queimadas_ Desmatamento)/yearly_deforestation_biome_mata_atlantica_v20260806.zip"),
            new DefinicaoFonteReferencia("MMA_UC_CNUC", "Cadastro Nacional de Unidades de Conservação (MMA)",
                    "MMA (Unidades de Conservação)/cnuc_2026_07.csv"),
            new DefinicaoFonteReferencia("SFB_FLORESTA_PUBLICA", "Cadastro Nacional de Florestas Públicas 2025 (SFB)",
                    "SFB (Florestas Públicas)/CNFP_2025.zip")
    );

    private final JdbcTemplate jdbcTemplate;
    private final GeoJsonArquivoService geoJsonArquivoService;
    private final ObjectMapper mapper = new ObjectMapper();

    public CargaCamadaReferenciaService(
            JdbcTemplate jdbcTemplate,
            GeoJsonArquivoService geoJsonArquivoService) {
        this.jdbcTemplate = jdbcTemplate;
        this.geoJsonArquivoService = geoJsonArquivoService;
    }

    public Map<String, Object> importarReferenciasGeoespaciais(String filtroFonte, int limitePorFonte) {
        Path pastaBase = localizarPastaGeoespaciais();
        Map<String, Object> relatorio = new LinkedHashMap<>();
        List<Map<String, Object>> resultadosFontes = new ArrayList<>();
        int totalInseridos = 0;

        for (DefinicaoFonteReferencia def : FONTES_GEOESPACIAIS) {
            if (filtroFonte != null && !filtroFonte.isBlank()
                    && !def.fonte().equalsIgnoreCase(filtroFonte.trim())) {
                continue;
            }

            Path arquivoOrigem = pastaBase.resolve(def.caminhoRelativo());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fonte", def.fonte());
            item.put("nomeCamada", def.nomeCamada());
            item.put("arquivo", def.caminhoRelativo());

            if (!Files.isRegularFile(arquivoOrigem)) {
                item.put("status", "ARQUIVO_NAO_ENCONTRADO");
                item.put("inseridos", 0);
                resultadosFontes.add(item);
                continue;
            }

            try {
                int inseridos = importarArquivoParaCamadaReferencia(def, arquivoOrigem, limitePorFonte);
                item.put("status", "CONCLUIDO");
                item.put("inseridos", inseridos);
                totalInseridos += inseridos;
            } catch (Exception e) {
                item.put("status", "ERRO");
                item.put("erro", e.getMessage());
                item.put("inseridos", 0);
            }
            resultadosFontes.add(item);
        }

        relatorio.put("pastaBase", pastaBase.toAbsolutePath().toString());
        relatorio.put("totalInseridos", totalInseridos);
        relatorio.put("fontes", resultadosFontes);
        return relatorio;
    }

    private int importarArquivoParaCamadaReferencia(
            DefinicaoFonteReferencia def,
            Path arquivoOrigem,
            int limitePorFonte) throws IOException {

        Path geoJsonTemp = geoJsonArquivoService.preparar(arquivoOrigem);
        List<Object[]> lote = new ArrayList<>();
        int inseridos = 0;
        int posicao = 0;

        try (JsonParser parser = mapper.getFactory().createParser(geoJsonTemp.toFile())) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("GeoJSON inválido gerado para a fonte " + def.fonte());
            }

            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String campo = parser.currentName();
                JsonToken valor = parser.nextToken();

                if (!"features".equals(campo) || valor != JsonToken.START_ARRAY) {
                    parser.skipChildren();
                    continue;
                }

                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (limitePorFonte > 0 && inseridos + lote.size() >= limitePorFonte) {
                        parser.skipChildren();
                        break;
                    }

                    JsonNode feicao = mapper.readTree(parser);
                    posicao++;

                    JsonNode geometria = feicao.get("geometry");
                    if (geometria == null || !geometria.isObject() || geometria.isEmpty()) {
                        continue;
                    }

                    String identificador = FeicaoCargaService.extrairCodigoImovel(feicao, posicao);
                    JsonNode propriedades = feicao.get("properties");

                    lote.add(new Object[] {
                            def.fonte(),
                            def.nomeCamada(),
                            identificador,
                            geometria.toString(),
                            propriedades != null && propriedades.isObject() ? propriedades.toString() : null
                    });

                    if (lote.size() >= LOTE_MAX) {
                        inseridos += descarregarLote(lote);
                    }
                }
            }
        } finally {
            geoJsonArquivoService.excluirTemporario(geoJsonTemp, arquivoOrigem);
        }

        inseridos += descarregarLote(lote);
        return inseridos;
    }

    private int descarregarLote(List<Object[]> lote) {
        if (lote.isEmpty()) {
            return 0;
        }
        jdbcTemplate.batchUpdate(SQL_INSERIR_REFERENCIA, lote);
        int qtd = lote.size();
        lote.clear();
        return qtd;
    }

    private Path localizarPastaGeoespaciais() {
        List<Path> candidatos = List.of(
                Paths.get("/home/niuan/Projetos/API/GEOESPACIAIS"),
                Paths.get("GEOESPACIAIS").toAbsolutePath(),
                Paths.get("../GEOESPACIAIS").toAbsolutePath().normalize(),
                Paths.get("../../GEOESPACIAIS").toAbsolutePath().normalize()
        );
        for (Path p : candidatos) {
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        return Paths.get("GEOESPACIAIS").toAbsolutePath();
    }
}
