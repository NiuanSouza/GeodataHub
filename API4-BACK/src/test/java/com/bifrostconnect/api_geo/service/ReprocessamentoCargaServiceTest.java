package com.bifrostconnect.api_geo.service;

import com.bifrostconnect.api_geo.entity.ArquivoOriginal;
import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.ProcessoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReprocessamentoCargaServiceTest {

    @Mock
    private ProcessoRepository processoRepository;

    @Mock
    private ArquivoUploadService arquivoUploadService;

    @Mock
    private AuditoriaLogService auditoriaLogService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ReprocessamentoCargaService reprocessamentoCargaService;

    private Processo processoAtivo;
    private MockMultipartFile arquivoValido;

    @BeforeEach
    void setUp() {
        processoAtivo = Processo.builder()
                .id(100L)
                .versaoFechada(false)
                .build();

        arquivoValido = new MockMultipartFile(
                "file",
                "biomas_corrigido.geojson",
                "application/geo+json",
                "{\"type\": \"FeatureCollection\", \"features\": []}".getBytes()
        );
    }

    @Test
    @DisplayName("Deve reprocessar carga com sucesso, marcando ocorrencias como CORRIGIDA e reiniciando esteira em VALIDACAO")
    void deveReprocessarCargaComSucesso() throws Exception {
        when(processoRepository.findById(100L)).thenReturn(Optional.of(processoAtivo));

        ArquivoOriginal arquivoMock = new ArquivoOriginal();
        arquivoMock.setId(20L);
        arquivoMock.setNomeOriginal("biomas_corrigido.geojson");
        arquivoMock.setHashSha256("abc123hash");

        when(arquivoUploadService.processarUpload(eq(100L), any(), eq(1L))).thenReturn(arquivoMock);

        ArquivoOriginal resultado = reprocessamentoCargaService.reprocessarCarga(100L, arquivoValido, 1L);

        assertNotNull(resultado);
        assertEquals(20L, resultado.getId());
        assertEquals("biomas_corrigido.geojson", resultado.getNomeOriginal());

        // Verifica limpeza de ocorrencias e logs da tentativa anterior
        verify(jdbcTemplate, times(1)).update(contains("UPDATE ocorrencia_validacao"), eq(100L));
        verify(jdbcTemplate, times(1)).update(contains("DELETE FROM log_validacao"), eq(100L));
        verify(jdbcTemplate, times(1)).update(contains("UPDATE processo"), eq(100L));

        // Verifica auditoria
        verify(auditoriaLogService, times(1)).registrarAuditoria(
                eq(1L),
                eq("REPROCESSAMENTO_CARGA"),
                contains("Novo Arquivo: biomas_corrigido.geojson")
        );
    }

    @Test
    @DisplayName("Deve impedir reprocessamento de processo com versao já fechada/oficial (409 CONFLICT)")
    void deveBloquearReprocessamentoEmVersaoFechada() {
        Processo processoFechado = Processo.builder()
                .id(200L)
                .versaoFechada(true)
                .build();

        when(processoRepository.findById(200L)).thenReturn(Optional.of(processoFechado));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                reprocessamentoCargaService.reprocessarCarga(200L, arquivoValido, 1L)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("fechada/publicada"));
        verifyNoInteractions(arquivoUploadService);
    }

    @Test
    @DisplayName("Deve retornar 404 NOT_FOUND se processo não existir")
    void deveRetornar404SeProcessoNaoExistir() {
        when(processoRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                reprocessamentoCargaService.reprocessarCarga(999L, arquivoValido, 1L)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(arquivoUploadService);
    }
}
