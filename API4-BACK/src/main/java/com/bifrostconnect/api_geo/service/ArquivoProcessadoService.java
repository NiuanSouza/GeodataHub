package com.bifrostconnect.api_geo.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.springframework.stereotype.Service;

import com.bifrostconnect.api_geo.entity.ArquivoOriginal;
import com.bifrostconnect.api_geo.entity.ArquivoProcessado;
import com.bifrostconnect.api_geo.repository.ArquivoOriginalRepository;
import com.bifrostconnect.api_geo.repository.ArquivoProcessadoRepository;

@Service
public class ArquivoProcessadoService {

    private final ArquivoOriginalRepository arquivoOriginalRepository;
    private final ArquivoProcessadoRepository arquivoProcessadoRepository;
    private final PadronizacaoEpsgService padronizacaoEpsgService;

    private final String STORAGE_DIR = "zona_processada_storage/";

    public ArquivoProcessadoService(
            ArquivoOriginalRepository arquivoOriginalRepository,
            ArquivoProcessadoRepository arquivoProcessadoRepository,
            PadronizacaoEpsgService padronizacaoEpsgService) {

        this.arquivoOriginalRepository = arquivoOriginalRepository;
        this.arquivoProcessadoRepository = arquivoProcessadoRepository;
        this.padronizacaoEpsgService = padronizacaoEpsgService;
    }

    public ArquivoProcessado criarCopiaPadronizada(
            Long processoId,
            Long etapaGeracaoId,
            Path geoJsonOrigem,
            String epsgDetectado) throws Exception {

        ArquivoOriginal original = arquivoOriginalRepository
                .findFirstByProcessoIdOrderByIdDesc(processoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nenhum arquivo original encontrado para o processo: " + processoId));

        Path diretorioProcessado = Paths.get(STORAGE_DIR);
        Files.createDirectories(diretorioProcessado);

        String nomeBase = nomeSemExtensao(original.getNomeOriginal());
        String nomeCopia = nomeBase + "_epsg" + PadronizacaoEpsgService.EPSG_OFICIAL + ".geojson";
        Path caminhoProcessado = diretorioProcessado.resolve(System.currentTimeMillis() + "_" + nomeCopia);

        // Original intocado: só le o  geoJsonOrigem e gravamos a cópia nova.
        padronizacaoEpsgService.converterParaEpsgOficial(geoJsonOrigem, epsgDetectado, caminhoProcessado);

        int proximaVersao = arquivoProcessadoRepository
                .findFirstByProcessoIdOrderByVersaoDesc(processoId)
                .map(ultimo -> ultimo.getVersao() + 1)
                .orElse(1);

        ArquivoProcessado processado = new ArquivoProcessado();
        processado.setArquivoOriginalId(original.getId());
        processado.setProcessoId(processoId);
        processado.setEtapaGeracaoId(etapaGeracaoId);
        processado.setNomeArquivo(nomeCopia);
        processado.setFormato("geojson");
        processado.setUrlArmazenamento(caminhoProcessado.toString());
        // o original continua em arquivo_original.
        processado.setHashSha256(padronizacaoEpsgService.calcularSha256(caminhoProcessado));
        processado.setEpsg(PadronizacaoEpsgService.EPSG_OFICIAL);
        processado.setVersao(proximaVersao);

        try {
            return arquivoProcessadoRepository.save(processado);
        } catch (RuntimeException e) {
            Files.deleteIfExists(caminhoProcessado); // evita arquivo perdido se o registro falhar
            throw e;
        }
    }

    private String nomeSemExtensao(String nome) {
        String base = nome == null || nome.isBlank() ? "arquivo" : Paths.get(nome).getFileName().toString();
        int ponto = base.lastIndexOf('.');
        if (ponto > 0) {
            base = base.substring(0, ponto);
        }
        String seguro = base.replaceAll("[^A-Za-z0-9._-]", "_");
        return seguro.isBlank() ? "arquivo" : seguro;
    }

    public ArquivoProcessado criarCopiaProcessada(
            Long arquivoOriginalId,
            Long processoId,
            Long etapaGeracaoId,
            String epsg) throws Exception {

        ArquivoOriginal original = arquivoOriginalRepository.findById(arquivoOriginalId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Arquivo original não encontrado: " + arquivoOriginalId));

        Path caminhoOriginal = Paths.get(original.getUrlArmazenamento());

        if (!Files.exists(caminhoOriginal)) {
            throw new IllegalArgumentException(
                    "Arquivo original não encontrado no armazenamento: "
                            + caminhoOriginal);
        }
        Path diretorioProcessado = Paths.get(STORAGE_DIR);

        if (!Files.exists(diretorioProcessado)) {
            Files.createDirectories(diretorioProcessado);
        }

        String nomeArquivo = original.getNomeOriginal();

        Path caminhoProcessado = diretorioProcessado.resolve(
                System.currentTimeMillis() + "_" + nomeArquivo
        );

        Files.copy(
                caminhoOriginal,
                caminhoProcessado,
                StandardCopyOption.COPY_ATTRIBUTES
        );

       
        ArquivoProcessado processado = new ArquivoProcessado();

        processado.setArquivoOriginalId(original.getId());
        processado.setProcessoId(processoId);
        processado.setEtapaGeracaoId(etapaGeracaoId);
        processado.setNomeArquivo(nomeArquivo);
        processado.setFormato(original.getExtensao());
        processado.setUrlArmazenamento(caminhoProcessado.toString());
        processado.setHashSha256(original.getHashSha256());
        processado.setEpsg(epsg);
        processado.setVersao(1);

        
        return arquivoProcessadoRepository.save(processado);
    }
}