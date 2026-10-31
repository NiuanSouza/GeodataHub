package com.bifrostconnect.api_geo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bifrostconnect.api_geo.entity.ArquivoOriginal;

public interface ArquivoOriginalRepository extends JpaRepository<ArquivoOriginal, Long> {

    boolean existsByHashSha256(String hashSha256);

    Optional<ArquivoOriginal> findFirstByProcessoIdOrderByIdDesc(Long processoId);

    Optional<ArquivoOriginal> findByProcessoId(Long processoId);

    List<ArquivoOriginal> findAllByProcessoIdOrderByIdAsc(Long processoId);
}