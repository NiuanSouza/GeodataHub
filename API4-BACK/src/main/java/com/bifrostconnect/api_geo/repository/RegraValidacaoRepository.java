package com.bifrostconnect.api_geo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bifrostconnect.api_geo.entity.RegraValidacao;

public interface RegraValidacaoRepository extends JpaRepository<RegraValidacao, Long> {
    List<RegraValidacao> findByAtivoTrue();

    Optional<RegraValidacao> findByNome(String nome);
}