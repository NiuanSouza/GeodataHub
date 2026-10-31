package com.bifrostconnect.api_geo.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.bifrostconnect.api_geo.entity.ArquivoOriginal;
import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.ArquivoOriginalRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArquivoService {

    private final ArquivoOriginalRepository repository;
    private final HashService hashService;

    // Diretório local que simula o Object Storage da Zona Bruta
    private static final String STORAGE_DIR = "zona_bruta_storage/";

    public ArquivoOriginal processarEArmazenarArquivo(MultipartFile file, Long processoId, Long usuarioUploadId) throws Exception {
        
        // 1. Gera o Hash SHA-256 do arquivo utilizando o serviço especialista (Tarefa 2)
        String hashSha256 = hashService.calcularHash(file);

        // 2. Verifica se o arquivo já foi enviado (evita duplicidade conforme a constraint UNIQUE)
        if (repository.existsByHashSha256(hashSha256)) {
            throw new RuntimeException("Este arquivo já foi processado anteriormente (Hash Duplicado).");
        }

        // 3. Salva o arquivo fisicamente no servidor (Zona Bruta)
        File diretorio = new File(STORAGE_DIR);
        if (!diretorio.exists()) {
            diretorio.mkdirs(); // Cria a pasta se não existir
        }
        
        String nomeArquivoOriginal = file.getOriginalFilename();
        Path caminhoFisico = Paths.get(STORAGE_DIR + System.currentTimeMillis() + "_" + nomeArquivoOriginal);
        Files.write(caminhoFisico, file.getBytes());

        // 4. Monta o objeto para salvar no banco (Regra de Imutabilidade - Tarefa 3)
        ArquivoOriginal registro = new ArquivoOriginal();
        Processo processoRef = new Processo();
        processoRef.setId(processoId);
        registro.setProcesso(processoRef);
        registro.setUsuarioUploadId(usuarioUploadId);
        registro.setNomeOriginal(nomeArquivoOriginal);
        
        // Captura a extensão
        if (nomeArquivoOriginal != null && nomeArquivoOriginal.contains(".")) {
            registro.setExtensao(nomeArquivoOriginal.substring(nomeArquivoOriginal.lastIndexOf(".")));
        }
        
        registro.setTipoMime(file.getContentType());
        registro.setTamanhoBytes(file.getSize());
        registro.setUrlArmazenamento(caminhoFisico.toString());
        registro.setHashSha256(hashSha256);
        registro.setImutavel(true);

        // 5. Salva no banco de dados e retorna os dados
        return repository.save(registro);
    }

    // busca o arquivo original pelo ID, lançando exceção se não encontrado
    public ArquivoOriginal buscarArquivoOriginal(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Arquivo original não encontrado: " + id));
    }
}