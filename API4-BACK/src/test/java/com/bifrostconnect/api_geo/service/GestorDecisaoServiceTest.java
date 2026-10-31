package com.bifrostconnect.api_geo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.bifrostconnect.api_geo.dto.DecisaoGestorRequest;
import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.ProcessoRepository;

class GestorDecisaoServiceTest {

    private ProcessoRepository processoRepository;
    private ProcessoEstadoService processoEstadoService;
    private AuditoriaLogService auditoriaLogService;
    private NotificacaoService notificacaoService;
    private ConsolidacaoPublicacaoService consolidacaoPublicacaoService;
    private JdbcTemplate jdbcTemplate;
    private GestorDecisaoService service;

    @BeforeEach
    void setUp() {
        processoRepository = mock(ProcessoRepository.class);
        processoEstadoService = mock(ProcessoEstadoService.class);
        auditoriaLogService = mock(AuditoriaLogService.class);
        notificacaoService = mock(NotificacaoService.class);
        consolidacaoPublicacaoService = mock(ConsolidacaoPublicacaoService.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        service = new GestorDecisaoService(
                processoRepository,
                processoEstadoService,
                auditoriaLogService,
                notificacaoService,
                consolidacaoPublicacaoService,
                jdbcTemplate);
    }

    @Test
    @DisplayName("Gate Validação: Gestor aprova e processo avança para Tratamento")
    void deveAprovarValidacaoEAvancarParaTratamento() {
        Processo processo = new Processo();
        processo.setId(10L);
        processo.setVersaoFechada(false);

        when(processoRepository.findById(10L)).thenReturn(Optional.of(processo));

        DecisaoGestorRequest request = DecisaoGestorRequest.builder()
                .gestorId(1L)
                .etapa("VALIDACAO")
                .decisao("APROVADO")
                .parecer("Conforme")
                .build();

        var res = service.processarDecisao(10L, request);

        assertTrue((Boolean) res.get("sucesso"));
        verify(processoEstadoService).atualizarEstado(eq(processo), eq("TRATAMENTO"), eq("EM_ANDAMENTO"), anyString());
        verify(auditoriaLogService).registrarAuditoria(eq(1L), eq("APROVACAO_GESTOR_VALIDACAO"), anyString());
    }

    @Test
    @DisplayName("Gate Tratamento: Gestor aprova e processo avança para Publicação")
    void deveAprovarTratamentoEAvancarParaPublicacao() {
        Processo processo = new Processo();
        processo.setId(10L);
        processo.setVersaoFechada(false);

        when(processoRepository.findById(10L)).thenReturn(Optional.of(processo));

        DecisaoGestorRequest request = DecisaoGestorRequest.builder()
                .gestorId(1L)
                .etapa("TRATAMENTO")
                .decisao("APROVADO")
                .build();

        var res = service.processarDecisao(10L, request);

        assertTrue((Boolean) res.get("sucesso"));
        verify(processoEstadoService).atualizarEstado(eq(processo), eq("PUBLICACAO"), eq("EM_ANDAMENTO"), anyString());
        verify(auditoriaLogService).registrarAuditoria(eq(1L), eq("APROVACAO_GESTOR_TRATAMENTO"), anyString());
    }

    @Test
    @DisplayName("Gate Publicação: Gestor homologa e fecha versão oficial")
    void deveAprovarPublicacaoEFecharVersao() {
        Processo processo = new Processo();
        processo.setId(10L);
        processo.setVersaoFechada(false);

        when(processoRepository.findById(10L)).thenReturn(Optional.of(processo));

        DecisaoGestorRequest request = DecisaoGestorRequest.builder()
                .gestorId(1L)
                .etapa("PUBLICACAO")
                .decisao("APROVADO")
                .build();

        var res = service.processarDecisao(10L, request);

        assertTrue((Boolean) res.get("sucesso"));
        assertTrue(processo.isVersaoFechada());
        assertEquals("1.0", processo.getVersaoOficial());
        verify(processoEstadoService).atualizarEstado(eq(processo), eq("PUBLICACAO"), eq("CONCLUIDA"), anyString());
        verify(auditoriaLogService).registrarAuditoria(eq(1L), eq("FECHAMENTO_INDICADOR_OFICIAL"), anyString());
    }

    @Test
    @DisplayName("Reprovação sem parecer deve lançar exceção")
    void deveExigirParecerNaReprovacao() {
        Processo processo = new Processo();
        processo.setId(10L);
        processo.setVersaoFechada(false);

        when(processoRepository.findById(10L)).thenReturn(Optional.of(processo));

        DecisaoGestorRequest request = DecisaoGestorRequest.builder()
                .gestorId(1L)
                .etapa("VALIDACAO")
                .decisao("REPROVADO")
                .parecer("") // vazio
                .build();

        assertThrows(ResponseStatusException.class, () -> service.processarDecisao(10L, request));
    }

    @Test
    @DisplayName("Reprovação com parecer move carga para Quarentena (EM_VALIDACAO)")
    void deveEnviarParaQuarentenaQuandoReprovado() {
        Processo processo = new Processo();
        processo.setId(10L);
        processo.setVersaoFechada(false);

        when(processoRepository.findById(10L)).thenReturn(Optional.of(processo));

        DecisaoGestorRequest request = DecisaoGestorRequest.builder()
                .gestorId(1L)
                .etapa("TRATAMENTO")
                .decisao("REPROVADO")
                .parecer("Sobreposição grave encontrada no polígono 4")
                .build();

        var res = service.processarDecisao(10L, request);

        assertTrue((Boolean) res.get("sucesso"));
        verify(processoEstadoService).atualizarEstado(eq(processo), eq("TRATAMENTO"), eq("EM_VALIDACAO"), anyString());
        verify(auditoriaLogService).registrarAuditoria(eq(1L), eq("REPROVACAO_GESTOR_TRATAMENTO"), anyString());
        verify(notificacaoService).notificarAuditores(eq(processo), anyString());
    }

    @Test
    @DisplayName("Alocação de Auditor pelo Gestor")
    void deveAlocarAuditorENotificar() {
        Processo processo = new Processo();
        processo.setId(10L);

        when(processoRepository.findById(10L)).thenReturn(Optional.of(processo));

        var res = service.alocarAuditor(10L, 2L, 1L);

        assertTrue((Boolean) res.get("sucesso"));
        assertEquals(2L, res.get("auditorId"));
        verify(auditoriaLogService).registrarAuditoria(eq(1L), eq("ALOCACAO_AUDITOR"), anyString());
    }
}
