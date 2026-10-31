package com.bifrostconnect.api_geo.repository;

import com.bifrostconnect.api_geo.entity.Notificacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    @Query(value = """
        SELECT u.id
        FROM usuario u
        INNER JOIN perfil p ON p.id = u.perfil_id
        WHERE p.nome = 'AUDITOR'
          AND u.ativo = TRUE
        """, nativeQuery = true)
    List<Long> findUsuariosAuditoresAtivos();

    List<Notificacao> findByUsuarioIdOrderByDataCriacaoDesc(Long usuarioId);
}