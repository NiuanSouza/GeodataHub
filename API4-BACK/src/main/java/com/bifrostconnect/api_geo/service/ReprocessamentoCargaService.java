package com.bifrostconnect.api_geo.service;

import com.bifrostconnect.api_geo.entity.ArquivoOriginal;
import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.ProcessoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ReprocessamentoCargaService {

    private final ProcessoRepository processoRepository;
    private final ArquivoUploadService arquivoUploadService;
    private final AuditoriaLogService auditoriaLogService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public ArquivoOriginal reprocessarCarga(Long processoId, MultipartFile file, Long usuarioId) {
        // 1. Verifica se o processo existe
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Processo não encontrado."));

        if (processo.isVersaoFechada()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta versão já foi fechada/publicada e não permite reprocessamento.");
        }

        try {
            // 2. Processa o upload do novo arquivo corrigido com tratamento de exceção
            ArquivoOriginal novoArquivo = arquivoUploadService.processarUpload(processo.getId(), file, usuarioId);

            // 3. Marca ocorrências anteriores como corrigidas para não contaminar a nova submissão
            jdbcTemplate.update("""
                UPDATE ocorrencia_validacao
                   SET status = 'CORRIGIDA', data_resolucao = now()
                 WHERE processo_id = ? AND status = 'PENDENTE'
                """, processoId);

            // 4. Limpa logs de validação espaciais anteriores para que a esteira inicie com estado limpo
            jdbcTemplate.update("DELETE FROM log_validacao WHERE processo_id = ?", processoId);

            // 5. Reinicia a situação do processo para EM_ANDAMENTO
            jdbcTemplate.update("""
                UPDATE processo
                   SET situacao_atual_id = COALESCE(
                       (SELECT id FROM situacao WHERE nome = 'EM_ANDAMENTO' LIMIT 1),
                       situacao_atual_id
                   )
                 WHERE id = ?
                """, processo.getId());

            // 6. Registra na auditoria
            auditoriaLogService.registrarAuditoria(
                    usuarioId,
                    "REPROCESSAMENTO_CARGA",
                    "Operador enviou novo arquivo corrigido para o Processo ID: " + processoId + 
                    ", Novo Arquivo: " + file.getOriginalFilename() + 
                    ", Novo Hash SHA-256 gerado."
            );

            return novoArquivo;

        } catch (IllegalArgumentException | org.springframework.dao.DataIntegrityViolationException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao processar o reupload do arquivo: " + e.getMessage());
        }
    }
}
