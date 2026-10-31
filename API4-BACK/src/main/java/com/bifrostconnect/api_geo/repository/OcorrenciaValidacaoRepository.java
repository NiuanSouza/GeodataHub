package com.bifrostconnect.api_geo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bifrostconnect.api_geo.entity.OcorrenciaValidacao;


public interface OcorrenciaValidacaoRepository extends JpaRepository<OcorrenciaValidacao, Long> {
    List<OcorrenciaValidacao> findByProcessoId(Long processoId);
}