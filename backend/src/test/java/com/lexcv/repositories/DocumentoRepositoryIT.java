package com.lexcv.repositories;

import com.lexcv.models.Documento;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressao: o id de {@link Documento} e atribuido pela aplicacao, nao pela base de dados.
 *
 * <p>A chave do objeto no storage e {@code <tenantId>/<documentoId>/<ficheiro>}, por isso o id
 * tem de existir antes do upload — e o upload corre antes do save, para que uma falha no storage
 * nao deixe uma linha a apontar para um objeto inexistente. Enquanto a entidade teve
 * {@code @GeneratedValue}, o Hibernate lia um id ja preenchido como entidade destacada: o
 * {@code save()} do Spring Data seguia por {@code merge()} em vez de {@code persist()}, emitia um
 * UPDATE a uma linha que nunca existiu e falhava com {@code ObjectOptimisticLockingFailureException}
 * — ou seja, todos os uploads devolviam HTTP 500.
 *
 * <p>O teste tem de bater na base de dados a serio: o teste unitario que ja existia para o upload
 * ({@code ResourceControllerUploadDocumentoTest}) faz mock do repositorio, logo a decisao
 * persist/merge — que e onde o defeito estava — nunca chegava a ser exercida.
 *
 * <p>Sobre as anotacoes, ver {@code NotificacaoRepositoryIT}: {@code @DataJpaTest} evita instanciar
 * MinioConfig/SecurityConfig e o {@code replace = NONE} impede a troca silenciosa da DataSource
 * do contentor por uma base embutida.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class DocumentoRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DocumentoRepository documentoRepository;

    @Autowired
    private EntityManager entityManager;

    private static Documento comId(UUID id, UUID tenantId, String nome) {
        return Documento.builder()
                .id(id)
                .tenantId(tenantId)
                .nome(nome)
                .tipo("ANEXO")
                .confidencialidade("PUBLICO")
                .caminhoArquivo(tenantId + "/" + id + "/" + nome)
                .tamanho(1024L)
                .mimeType("application/pdf")
                .versao(1)
                .build();
    }

    @Test
    void guardaDocumentoComIdAtribuidoPelaAplicacao() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        Documento guardado = documentoRepository.save(comId(id, tenantId, "Procuracao.pdf"));
        entityManager.flush();
        entityManager.clear();

        assertThat(guardado.getId()).isEqualTo(id);
        assertThat(documentoRepository.findById(id))
                .as("o id atribuido pela aplicacao tem de sobreviver ao save")
                .isPresent()
                .get()
                .satisfies(d -> {
                    assertThat(d.getNome()).isEqualTo("Procuracao.pdf");
                    assertThat(d.getCaminhoArquivo()).isEqualTo(tenantId + "/" + id + "/Procuracao.pdf");
                });
    }

    @Test
    void guardarNovaVersaoAtualizaALinhaExistenteSemCriarOutra() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        documentoRepository.save(comId(id, tenantId, "Contestacao.pdf"));
        entityManager.flush();
        entityManager.clear();

        Documento existente = documentoRepository.findById(id).orElseThrow();
        existente.setNome("Contestacao_v2.pdf");
        existente.setVersao(2);
        documentoRepository.save(existente);
        entityManager.flush();
        entityManager.clear();

        assertThat(documentoRepository.findById(id)).get().satisfies(d -> {
            assertThat(d.getNome()).isEqualTo("Contestacao_v2.pdf");
            assertThat(d.getVersao()).isEqualTo(2);
        });
        assertThat(documentoRepository.count())
                .as("versionar substitui a linha, nao acrescenta outra")
                .isEqualTo(1);
    }
}
