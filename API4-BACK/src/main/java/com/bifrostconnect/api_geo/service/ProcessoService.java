package com.bifrostconnect.api_geo.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.bifrostconnect.api_geo.dto.ResultadoCalculoAnalitico;
import com.bifrostconnect.api_geo.dto.ResultadoValidacaoTopologica;
import com.bifrostconnect.api_geo.entity.ArquivoOriginal;
import com.bifrostconnect.api_geo.entity.ArquivoProcessado;
import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.exception.FalhaGeoespacialException;
import com.bifrostconnect.api_geo.repository.ArquivoOriginalRepository;
import com.bifrostconnect.api_geo.repository.ProcessoRepository;

@Service
public class ProcessoService {

    private final ProcessoRepository processoRepository;
    private final ValidacaoService validacaoService;
    private final AuditoriaLogService auditoriaLogService;
    private final ProcessoEstadoService processoEstadoService;
    private final GeoJsonEpsgService geoJsonEpsgService;
    private final GeoJsonArquivoService geoJsonArquivoService;
    private final PadronizacaoEpsgService padronizacaoEpsgService;
    private final ArquivoProcessadoService arquivoProcessadoService;
    private final ValidacaoTopologicaService validacaoTopologicaService;
    private final CalculoAnaliticoService calculoAnaliticoService;
    private final NotificacaoService notificacaoService;
    private final JdbcTemplate jdbcTemplate;
    private final ArquivoOriginalRepository arquivoOriginalRepository;

    public ProcessoService(
            ProcessoRepository processoRepository,
            ValidacaoService validacaoService,
            AuditoriaLogService auditoriaLogService,
            ProcessoEstadoService processoEstadoService,
            GeoJsonEpsgService geoJsonEpsgService,
            GeoJsonArquivoService geoJsonArquivoService,
            PadronizacaoEpsgService padronizacaoEpsgService,
            ArquivoProcessadoService arquivoProcessadoService,
            ValidacaoTopologicaService validacaoTopologicaService,
            CalculoAnaliticoService calculoAnaliticoService,
            NotificacaoService notificacaoService,
            JdbcTemplate jdbcTemplate,
            ArquivoOriginalRepository arquivoOriginalRepository
        ) {

        this.processoRepository = processoRepository;
        this.validacaoService = validacaoService;
        this.auditoriaLogService = auditoriaLogService;
        this.processoEstadoService = processoEstadoService;
        this.geoJsonEpsgService = geoJsonEpsgService;
        this.geoJsonArquivoService = geoJsonArquivoService;
        this.padronizacaoEpsgService = padronizacaoEpsgService;
        this.arquivoProcessadoService = arquivoProcessadoService;
        this.validacaoTopologicaService = validacaoTopologicaService;
        this.calculoAnaliticoService = calculoAnaliticoService;
        this.notificacaoService = notificacaoService;
        this.jdbcTemplate = jdbcTemplate;
        this.arquivoOriginalRepository = arquivoOriginalRepository;
    }

    public Processo buscarPorId(Long id) {
        return processoRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Processo não encontrado para o ID: " + id));
    }

    // Retorna 409 (Conflict) caso receba comandos de edição para um ID de versão que já foi fechada
    private void verificarSeFechado(Processo processo) {
        if (processo.getSituacaoAtualId() != null && processo.getSituacaoAtualId().equals(999L)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflito: A versão/processo já foi fechada e não permite edições.");
        }
    }

    public List<?> listarAuditoresReais() {
        return List.of();
    }

    @Transactional
    public Processo alocarEditorEAtualizarSituacao(
            Long processoId,
            Long editorId) {

        Processo processo = buscarPorId(processoId);
        verificarSeFechado(processo);

        processoEstadoService.atualizarEstado(
                processo,
                "TRATAMENTO",
                "EM_ANDAMENTO",
                "Processo alocado para processamento.");

        auditoriaLogService.registrarLog(
                processo,
                "INFO",
                "Processo colocado em andamento pelo editor ID: " + editorId);

        return salvarGarantindoCampos(processo);
    }

    /**
     * Executa a esteira geoespacial de processamento de forma assíncrona em segundo plano,
     * liberando a thread HTTP e a interface de upload para resposta imediata.
     */
    @Async
    public CompletableFuture<Processo> processarArquivoAsync(Long processoId, Long usuarioId) {
        try {
            Processo processo = buscarPorId(processoId);
            ArquivoOriginal arquivo = arquivoOriginalRepository
                    .findFirstByProcessoIdOrderByIdDesc(processoId)
                    .orElse(null);

            if (arquivo == null || arquivo.getUrlArmazenamento() == null) {
                auditoriaLogService.registrarLog(processo, "ERROR", "Arquivo físico não encontrado para processamento assíncrono.");
                return CompletableFuture.completedFuture(processo);
            }

            Path caminhoArquivo = Paths.get(arquivo.getUrlArmazenamento());
            Processo resultado = processarArquivo(processo, arquivo.getNomeOriginal(), caminhoArquivo, usuarioId);
            return CompletableFuture.completedFuture(resultado);
        } catch (FalhaGeoespacialException e) {
            // A carga já foi devidamente encaminhada para a Quarentena pelo processarArquivo
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            try {
                Processo p = buscarPorId(processoId);
                processoEstadoService.atualizarEstado(p, "VALIDACAO", "FALHOU", "Erro no processamento em segundo plano: " + e.getMessage());
                auditoriaLogService.registrarLog(p, "ERROR", "Falha no processamento em segundo plano: " + e.getMessage());
            } catch (Exception ignored) {}
            return CompletableFuture.completedFuture(null);
        }
    }

    // noRollbackFor: quando a etapa termina em falha controlada (422/415) o log de erro, a
    // auditoria e o EPSG detectado já gravados NÃO podem ser desfeitos pelo rollback - senão
    // o processo ficaria FALHOU sem nenhuma explicação registrada
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Processo processarArquivo(
            Processo processo,
            String nomeArquivo,
            Path caminhoArquivo,
            Long usuarioId) {

        verificarSeFechado(processo);

        if (caminhoArquivo == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "O arquivo é obrigatório para iniciar o tratamento.");
        }

        Path caminhoGeoJson = null;

        try {
            caminhoGeoJson = geoJsonArquivoService.preparar(caminhoArquivo);

            return processarArquivoGeoJson(
                    processo,
                    nomeArquivo,
                    caminhoGeoJson,
                    usuarioId);

        } catch (ResponseStatusException e) {
            throw e;

        } catch (IllegalArgumentException e) {
            String mensagem = "Falha ao preparar o arquivo: " + e.getMessage();
            try {
                processoEstadoService.atualizarEstado(processo, "TRATAMENTO", "FALHOU", mensagem);
                auditoriaLogService.registrarLog(processo, "ERROR", mensagem);
                salvarGarantindoCampos(processo);
            } catch (Exception ignored) {
            }
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    e.getMessage(),
                    e);

        } catch (IOException e) {
            String mensagem = "Não foi possível ler ou preparar o arquivo: " + e.getMessage();
            try {
                processoEstadoService.atualizarEstado(processo, "TRATAMENTO", "FALHOU", mensagem);
                auditoriaLogService.registrarLog(processo, "ERROR", mensagem);
                salvarGarantindoCampos(processo);
            } catch (Exception ignored) {
            }
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    mensagem,
                    e);

        } finally {
            if (caminhoGeoJson != null && !caminhoGeoJson.equals(caminhoArquivo)) {
                try {
                    Files.deleteIfExists(caminhoGeoJson);
                } catch (IOException e) {
                    auditoriaLogService.registrarLog(
                            processo,
                            "WARNING",
                            "Não foi possível excluir o GeoJSON temporário: "
                                    + e.getMessage());
                }
            }
        }
    }

    // etapa VALIDACAO: padronização, checagem de EPSG, topologia e cálculos preliminares
    private Processo processarArquivoGeoJson(
            Processo processo,
            String nomeOriginal,
            Path caminhoGeoJson,
            Long usuarioId) {

        processoEstadoService.atualizarEstado(
                processo,
                "VALIDACAO",
                "EM_ANDAMENTO",
                "Validação iniciada: padronização e checagem de EPSG.");

        auditoriaLogService.registrarLog(
                processo,
                "INFO",
                "Iniciando fluxo do processo para o arquivo: " + nomeOriginal);

        auditoriaLogService.registrarAuditoria(
                usuarioId,
                "INICIO_PROCESSO",
                "Processo ID: " + processo.getId());

        String epsgDeclarado = null;
        String epsgDetectado = null;
        ArquivoProcessado copiaTrabalho;

        try {
            epsgDeclarado = padronizacaoEpsgService.normalizarEpsg(processo.getEpsgOrigem());
            geoJsonEpsgService.validarCodigoEpsg(epsgDeclarado);

            epsgDetectado = geoJsonEpsgService.detectar(caminhoGeoJson);

            // EPSG detectado fica em coluna própria, separada do declarado (epsg_origem).
            processo.setEpsgDetectado(epsgDetectado);

            PadronizacaoEpsgService.ResultadoChecagem checagem =
                    padronizacaoEpsgService.comparar(epsgDeclarado, epsgDetectado);

            if (checagem.divergente()) {

                String mensagem = "Divergência de EPSG: declarado EPSG:"
                        + epsgDeclarado
                        + ", detectado EPSG:"
                        + epsgDetectado
                        + ". Validação com ressalvas. Corrija manualmente o cadastro "
                        + "ou o arquivo e envie novamente para validação.";

                processoEstadoService.atualizarEstado(
                        processo,
                        "VALIDACAO",
                        "COM_RESSALVA",
                        mensagem);

                auditoriaLogService.registrarLog(
                        processo,
                        "WARNING",
                        mensagem);

                auditoriaLogService.registrarAuditoria(
                        usuarioId,
                        "DIVERGENCIA_EPSG",
                        "Processo ID: " + processo.getId()
                                + "; declarado=" + epsgDeclarado
                                + "; detectado=" + epsgDetectado);

                // Interrompido: sem cópia de trabalho e sem validações adicionais.
                return salvarGarantindoCampos(processo);
            }

            geoJsonEpsgService.validarCodigoEpsg(epsgDetectado);

            copiaTrabalho = arquivoProcessadoService.criarCopiaPadronizada(
                    processo.getId(),
                    processo.getEtapaAtualId(), // VALIDACAO: etapa que gerou a cópia
                    caminhoGeoJson,
                    epsgDetectado);

            processo.setEpsgDestino(PadronizacaoEpsgService.EPSG_OFICIAL);

        } catch (FalhaGeoespacialException e) {
            // Tarefa 15: tem que vir antes do ResponseStatusException (ela é filha dele)
            throw enviarParaQuarentenaGeoespacial(processo, usuarioId, e);

        } catch (ResponseStatusException e) {
            throw e;

        } catch (Exception e) {
            String mensagem = "Falha na padronização/verificação do EPSG: "
                    + e.getMessage();

            processoEstadoService.atualizarEstado(
                    processo,
                    "VALIDACAO",
                    "FALHOU",
                    mensagem);

            auditoriaLogService.registrarLog(
                    processo,
                    "ERROR",
                    mensagem);

            salvarGarantindoCampos(processo);

            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    mensagem,
                    e);
        }

        auditoriaLogService.registrarLog(
                processo,
                "INFO",
                "EPSG padronizado: declarado EPSG:" + epsgDeclarado
                        + ", detectado EPSG:" + epsgDetectado
                        + ", cópia de trabalho em EPSG:" + PadronizacaoEpsgService.EPSG_OFICIAL
                        + " (arquivo_processado ID: " + copiaTrabalho.getId() + ").");

        // 2ª parte: checa a topologia dos polígonos da cópia de trabalho
        processoEstadoService.atualizarEstado(
                processo,
                "VALIDACAO",
                "EM_ANDAMENTO",
                "Validação: checagem topológica dos polígonos.");

        ResultadoValidacaoTopologica topologia;

        try {
            topologia = validacaoTopologicaService.validar(processo, copiaTrabalho);

        } catch (FalhaGeoespacialException e) {
            throw enviarParaQuarentenaGeoespacial(processo, usuarioId, e);

        } catch (Exception e) {
            String mensagem = "Falha na validação topológica dos polígonos: "
                    + e.getMessage();

            processoEstadoService.atualizarEstado(
                    processo,
                    "VALIDACAO",
                    "FALHOU",
                    mensagem);

            auditoriaLogService.registrarLog(
                    processo,
                    "ERROR",
                    mensagem);

            salvarGarantindoCampos(processo);

            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    mensagem,
                    e);
        }

        auditoriaLogService.registrarLog(
                processo,
                topologia.possuiProblemas() ? "WARNING" : "INFO",
                "Validação topológica: " + topologia.feicoesCarregadas() + " de "
                        + topologia.totalFeicoes() + " feições carregadas; "
                        + topologia.contar(ResultadoValidacaoTopologica.TipoProblema.GEOMETRIA_INVALIDA) + " inválidas/abertas, "
                        + topologia.contar(ResultadoValidacaoTopologica.TipoProblema.CODIGO_IMOVEL_DUPLICADO) + " códigos repetidos, "
                        + topologia.contar(ResultadoValidacaoTopologica.TipoProblema.POLIGONO_DUPLICADO) + " duplicados, "
                        + topologia.contar(ResultadoValidacaoTopologica.TipoProblema.POLIGONO_SOBREPOSTO) + " sobreposições.");

        // Executa o cálculo analítico sobre os polígonos válidos carregados
        // para que os cruzamentos com APP, Reserva Legal e Alertas fiquem disponíveis
        // para o Gestor e Auditor mesmo se houver inconsistências topológicas.
        ResultadoCalculoAnalitico calculo = null;
        try {
            calculo = calculoAnaliticoService.calcular(processo, copiaTrabalho);
            auditoriaLogService.registrarLog(
                    processo,
                    "INFO",
                    "Cálculo analítico: " + calculo.poligonosCalculados() + " polígonos válidos somando "
                            + calculo.areaTotalHectares().toPlainString() + " ha; sobreposição com APP "
                            + calculo.hectaresPorCategoria(ResultadoCalculoAnalitico.Categoria.APP).toPlainString() + " ha ("
                            + calculo.contar(ResultadoCalculoAnalitico.Categoria.APP) + " cruzamentos), Reserva Legal "
                            + calculo.hectaresPorCategoria(ResultadoCalculoAnalitico.Categoria.RESERVA_LEGAL).toPlainString() + " ha ("
                            + calculo.contar(ResultadoCalculoAnalitico.Categoria.RESERVA_LEGAL) + " cruzamentos), Alertas "
                            + calculo.hectaresPorCategoria(ResultadoCalculoAnalitico.Categoria.ALERTA).toPlainString() + " ha ("
                            + calculo.contar(ResultadoCalculoAnalitico.Categoria.ALERTA) + " cruzamentos).");
        } catch (Exception e) {
            auditoriaLogService.registrarLog(
                    processo,
                    "WARNING",
                    "Cálculo analítico preliminar suspenso: " + e.getMessage());
        }

        List<ResultadoValidacaoTopologica.Problema> inaceitaveis = topologia.problemasInaceitaveis();
        if (!inaceitaveis.isEmpty()) {
            return enviarParaQuarentena(
                    processo,
                    usuarioId,
                    "QUARENTENA_TOPOLOGIA",
                    "Validação topológica encontrou " + inaceitaveis.size()
                            + " sobreposição(ões)/duplicação(ões) inaceitável(is).",
                    inaceitaveis.stream().map(ResultadoValidacaoTopologica.Problema::mensagem).toList());
        }

        if (calculo != null && calculo.possuiInconsistencias()) {
            return enviarParaQuarentena(
                    processo,
                    usuarioId,
                    "QUARENTENA_CALCULO",
                    "Cálculo analítico encontrou " + calculo.inconsistencias().size()
                            + " inconsistência(s) matemática(s)/geométrica(s).",
                    calculo.inconsistencias());
        }

        processoEstadoService.atualizarEstado(
                processo,
                "VALIDACAO",
                "EM_ANDAMENTO",
                "Ingestão e pré-validação concluídas. Aguardando avaliação e aprovação do Gestor no gate de Validação.");

        auditoriaLogService.registrarLog(
                processo,
                "INFO",
                "Arquivo processado e validado. Aguardando decisão do Gestor na etapa de Validação.");

        return salvarGarantindoCampos(processo);
    }


    // Deixa a carga parada em VALIDACAO / EM_VALIDACAO (quarentena) esperando a decisão do Gestor.
    private Processo enviarParaQuarentena(
            Processo processo,
            Long usuarioId,
            String acaoAuditoria,
            String motivo,
            List<String> detalhes) {

        return enviarParaQuarentena(processo, usuarioId, acaoAuditoria, motivo, detalhes, null, null);
    }

    private Processo enviarParaQuarentena(
            Processo processo,
            Long usuarioId,
            String acaoAuditoria,
            String motivo,
            List<String> detalhes,
            String codigoErro,
            String detalheTecnico) {

        String mensagem = motivo
                + " Carga encaminhada para a Quarentena na etapa de Validação, aguardando parecer do Gestor.";

        processoEstadoService.atualizarEstado(
                processo,
                "VALIDACAO",
                "EM_VALIDACAO",
                mensagem);

        // detalhe completo já fica em ocorrencia_validacao (topologia); no log vai só uma amostra
        int limite = 10;
        String amostra = String.join(" ", detalhes.subList(0, Math.min(limite, detalhes.size())))
                + (detalhes.size() > limite ? " (+" + (detalhes.size() - limite) + " outros)" : "");

        auditoriaLogService.registrarLog(
                processo,
                "WARNING",
                mensagem + (amostra.isBlank() ? "" : " Detalhes: " + amostra),
                codigoErro,
                detalheTecnico);

        auditoriaLogService.registrarAuditoria(
                usuarioId,
                acaoAuditoria,
                "Processo ID: " + processo.getId() + "; " + motivo);

        notificacaoService.notificarAuditores(
                processo,
                "Carga do processo " + processo.getId() + " está em Quarentena na etapa de Validação: " + motivo);

        return salvarGarantindoCampos(processo);
    }

    // Tarefa 15: o PostGIS estourou numa conta geométrica ou na reprojeção (divisão por zero,
    // erro de projeção, TopologyException). O ExecucaoGeoespacial já voltou o savepoint, então a
    // transação ainda grava: manda a carga pra Quarentena com o código GEO_... no log e devolve
    // a própria exceção (422) pro controller. O noRollbackFor do processarArquivo segura o commit
    private FalhaGeoespacialException enviarParaQuarentenaGeoespacial(
            Processo processo,
            Long usuarioId,
            FalhaGeoespacialException falha) {

        enviarParaQuarentena(
                processo,
                usuarioId,
                "QUARENTENA_GEOESPACIAL",
                "Falha geoespacial na " + falha.getOperacao() + " (" + falha.getTipo().getDescricao() + ").",
                List.of(falha.getDetalheTecnico()),
                falha.getCodigoErro(),
                falha.getDetalheTecnico());

        return falha;
    }

    @Transactional
    public void fecharIndicador(Long id, Long gestorId) {
        Processo processo = buscarPorId(id);
        if (processo.isVersaoFechada()) {
            return; // Já fechado e imutável pelo banco, retorno idempotente com sucesso
        }

        Integer aprovacoes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM historico_aprovacoes_gestor WHERE processo_id = ? AND etapa_avaliada = 'PUBLICACAO' AND decisao = 'APROVADO'",
                Integer.class, processo.getId());
        if (aprovacoes == null || aprovacoes == 0) {
            jdbcTemplate.update("""
                INSERT INTO historico_aprovacoes_gestor
                    (processo_id, gestor_id, etapa_avaliada, decisao, parecer_justificativa, data_decisao)
                VALUES (?, ?, 'PUBLICACAO', 'APROVADO', 'Fechamento oficial de versão.', now())
                """, processo.getId(), gestorId);
        }

        processoEstadoService.atualizarEstado(
                processo,
                "PUBLICACAO",
                "CONCLUIDA",
                "Indicador fechado oficialmente pelo gestor."
        );

        jdbcTemplate.update("""
            UPDATE processo
            SET versao_fechada = TRUE,
                versao_oficial = '1.0',
                data_publicacao = now(),
                gestor_publicacao_id = ?
            WHERE id = ?
            """, gestorId, processo.getId());

        processo.setVersaoFechada(true);
        processo.setVersaoOficial("1.0");
        processo.setDataPublicacao(LocalDateTime.now());
        processo.setGestorPublicacaoId(gestorId);

        auditoriaLogService.registrarAuditoria(
                gestorId,
                "FECHAMENTO_INDICADOR_OFICIAL",
                "Canetada oficial aplicada ao indicador/processo ID: " + processo.getId() + " pelo Gestor ID: " + gestorId
        );

        try {
            jdbcTemplate.update("""
                UPDATE publicacao
                SET status = 'PUBLICADO', data_publicacao = now()
                WHERE processo_id = ?
                """, processo.getId());
        } catch (Exception ignored) {
        }

        try {
            jdbcTemplate.execute("SELECT fn_atualizar_dados_publicados()");
        } catch (Exception ignored) {
        }
    }

    public Resource exportarArquivoConsolidado(Long id, String formato) {
        Processo processo = buscarPorId(id);

        try {
            Path pastaProcessada = Path.of("zona_processada_storage");
            if (!Files.exists(pastaProcessada)) {
                Files.createDirectories(pastaProcessada);
            }

            String extensao = formato != null && formato.equalsIgnoreCase("csv") ? "csv" : "geojson";
            String nomeArquivo = id + "_exportado." + extensao;
            Path caminhoArquivo = pastaProcessada.resolve(nomeArquivo);

            if (!Files.exists(caminhoArquivo)) {
                String conteudo = extensao.equals("csv")
                    ? "id,situacao_atual_id,data_criacao\n" + processo.getId() + "," + processo.getSituacaoAtualId() + "," + processo.getDataCriacao()
                    : "{\"type\": \"FeatureCollection\", \"features\": []}";
                Files.writeString(caminhoArquivo, conteudo);
            }

            Resource recurso = new UrlResource(caminhoArquivo.toUri());
            if (recurso.exists() && recurso.isReadable()) {
                return recurso;
            } else {
                throw new RuntimeException("Não foi possível ler o arquivo exportado.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar exportação para o processo ID: " + id, e);
        }
    }

    private Processo salvarGarantindoCampos(Processo processo) {
        if (processo.getDataCriacao() == null) {
            processo.setDataCriacao(LocalDateTime.now());
        }
        if (processo.getAnoSafra() == null) {
            processo.setAnoSafra(String.valueOf(LocalDateTime.now().getYear()));
        }
        return processoRepository.save(processo);
    }
}