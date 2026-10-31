package com.bifrostconnect.api_geo.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bifrostconnect.api_geo.entity.ArquivoProcessado;


public interface ArquivoProcessadoRepository extends JpaRepository<ArquivoProcessado, Long> {

    // Usado para numerar a próxima versão da cópia de trabalho do processo.
    Optional<ArquivoProcessado> findFirstByProcessoIdOrderByVersaoDesc(Long processoId);
}