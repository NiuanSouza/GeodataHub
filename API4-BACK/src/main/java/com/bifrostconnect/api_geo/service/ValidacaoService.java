package com.bifrostconnect.api_geo.service;

import java.nio.file.Path;
import java.util.List;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;
import org.springframework.stereotype.Service;

import com.bifrostconnect.api_geo.entity.CamadaReferencia;
import com.bifrostconnect.api_geo.entity.OcorrenciaValidacao;
import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.entity.RegraValidacao;
import com.bifrostconnect.api_geo.repository.AnaliseEspacialRepository;
import com.bifrostconnect.api_geo.repository.CamadaReferenciaRepository;
import com.bifrostconnect.api_geo.repository.OcorrenciaValidacaoRepository;
import com.bifrostconnect.api_geo.repository.RegraValidacaoRepository;

@Service
public class ValidacaoService {

    private final RegraValidacaoRepository regraRepository;
    private final OcorrenciaValidacaoRepository ocorrenciaRepository;
    private final AnaliseEspacialRepository analiseEspacialRepository;
    private final CamadaReferenciaRepository camadaReferenciaRepository; // 1. Novo Repositório

    public ValidacaoService(RegraValidacaoRepository regraRepository,
                            OcorrenciaValidacaoRepository ocorrenciaRepository,
                            AnaliseEspacialRepository analiseEspacialRepository,
                            CamadaReferenciaRepository camadaReferenciaRepository) {
        this.regraRepository = regraRepository;
        this.ocorrenciaRepository = ocorrenciaRepository;
        this.analiseEspacialRepository = analiseEspacialRepository;
        this.camadaReferenciaRepository = camadaReferenciaRepository;
    }

    public boolean executarValidacoes(Processo processo, String nomeArquivo) {
        return executarValidacoes(processo, nomeArquivo, null);
    }

    public boolean executarValidacoes(Processo processo, String nomeArquivo, Path caminhoArquivo) {
        List<RegraValidacao> regras = regraRepository.findByAtivoTrue();
        boolean possuiErros = false;
        WKTReader wktReader = new WKTReader(); // Leitor JTS para converter String WKT em Geometry

        for (RegraValidacao regra : regras) {
            
            // 1. Validação de extensão de arquivo (MANTIDO)
            if ("VALIDAR_EXTENSAO".equalsIgnoreCase(regra.getNome())) {
                if (!nomeArquivo.endsWith(".zip") && !nomeArquivo.endsWith(".geojson") && !nomeArquivo.endsWith(".shp")) {
                    registrarOcorrencia(processo, regra, "ERRO", "Extensão de arquivo não suportada para validação geo.");
                    possuiErros = true;
                }
            }

            // 2. Validação de Sobreposição de Processos (MANTIDO)
            if ("VALIDAR_SOBREPOSICAO".equalsIgnoreCase(regra.getNome()) && nomeArquivo.endsWith(".geojson") && caminhoArquivo != null) {
                try {
                    List<String> geometriasWkt = GeoJsonLeitorUtil.extrairGeometriasWkt(caminhoArquivo);
                    for (String wktGeom : geometriasWkt) {
                        boolean sobreposto = analiseEspacialRepository.existeSobreposicao(wktGeom, processo.getId());
                        if (sobreposto) {
                            registrarOcorrencia(processo, regra, "ERRO", "Detectada sobreposição de polígonos com outro processo existente.");
                            possuiErros = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    registrarOcorrencia(processo, regra, "ERRO", "Falha ao processar análise espacial: " + e.getMessage());
                    possuiErros = true;
                }
            }

            // 3. NOVA REGRA: Validação de Restrições Ambientais e Territoriais
            if ("VERIFICAR_RESTRICOES_AMBIENTAIS".equalsIgnoreCase(regra.getNome()) && nomeArquivo.endsWith(".geojson") && caminhoArquivo != null) {
                try {
                    List<String> geometriasWkt = GeoJsonLeitorUtil.extrairGeometriasWkt(caminhoArquivo);
                    List<String> fontesRestritivas = List.of("IBAMA_EMBARGO", "FUNAI_TERRA_INDIGENA", "ICMBIO_UC_FEDERAL", "INCRA_QUILOMBOLA");

                    for (String wktGeom : geometriasWkt) {
                        Geometry geometriaImovel = wktReader.read(wktGeom);
                        geometriaImovel.setSRID(4674); // Define SRID padrão
                        
                        List<CamadaReferencia> restricoes = camadaReferenciaRepository.buscarSobreposicoes(fontesRestritivas, geometriaImovel);
                        
                        if (!restricoes.isEmpty()) {
                            for (CamadaReferencia restricao : restricoes) {
                                registrarOcorrencia(processo, regra, "ALERTA_GRAVE", 
                                    "Sobreposição detectada com área restrita: " + restricao.getNomeCamada());
                            }
                            possuiErros = true;
                        }
                    }
                } catch (Exception e) {
                    registrarOcorrencia(processo, regra, "ERRO", "Falha ao verificar restrições ambientais: " + e.getMessage());
                    possuiErros = true;
                }
            }
        }
        return !possuiErros;
    }

    private void registrarOcorrencia(Processo processo, RegraValidacao regra, String severidade, String mensagem) {
        OcorrenciaValidacao ocorrencia = new OcorrenciaValidacao();
        ocorrencia.setProcesso(processo);
        ocorrencia.setRegra(regra);
        ocorrencia.setSeveridade(severidade);
        ocorrencia.setMensagem(mensagem);
        ocorrenciaRepository.save(ocorrencia);
    }
}