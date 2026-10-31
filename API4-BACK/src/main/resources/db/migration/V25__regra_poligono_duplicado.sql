-- V25: regra para validação topológica
-- Todo problema que o motor acha vira uma ocorrencia_validacao, e ela precisa
-- de uma regra cadastrada. 
--   GEOMETRIA_INVALIDA       -> geometria aberta, sem área ou inválida
--   SOBREPOSICAO_PROPRIEDADE -> polígonos da carga encavalados entre si
--   CAR_DUPLICADO            -> mesmo código de imóvel repetido no arquivo
-- (faltava de polígono com geometria idêntica a outro, que não é a mesma)
-- coisa que sobreposição parcial nem que código repetido.

INSERT INTO regra_validacao (nome, descricao, tipo, severidade)
VALUES ('POLIGONO_DUPLICADO', 'Poligono com geometria identica a outro poligono da mesma carga', 'TOPOLOGIA', 'CRITICA')
ON CONFLICT (nome) DO NOTHING;
