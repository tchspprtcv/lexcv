# Pacote XSD eFatura (DFE) — versão técnica 2024-05-27

> **Cópia não oficial; a sua inclusão não implica aprovação da DNRE.**

Esta pasta contém os 22 ficheiros XSD do pacote técnico eFatura de Cabo Verde (Documento Fiscal
Eletrónico, DFE), versão datada de **2024-05-27**. São usados apenas para validação local do XML
gerado pelo LexCV e para gerar o modelo JAXB no build (`jaxb-maven-plugin`, ponto de entrada
`EnvelopedSignature.xsd`). Não são servidos a clientes nem alterados.

## Proveniência

- **Origem:** repositório público `Kowts/efatura-cv-php`, caminho
  `resources/xsd/efatura/2024-05-27/`.
- **Obtido:** 2026-10-05, ficheiro a ficheiro, via `https://raw.githubusercontent.com/Kowts/efatura-cv-php/main/resources/xsd/efatura/2024-05-27/`,
  copiado byte a byte (sem reescrita por editor). Todos os ficheiros são UTF-8 com fins de linha LF.
- **Porque não `efatura.cv`:** o acesso a `efatura.cv` (e subdomínios `services.`, `dev.`, `tst.`,
  `pe.`, …) está bloqueado pelo proxy de saída deste ambiente (`403 CONNECT`, política da
  organização). Não foi tentado qualquer contorno. Os itens G1 (atualidade do pacote) e G14
  (licença/redistribuição) do portão das fontes primárias da Phase 136 ficam **pendentes** para
  decisão do utilizador no fim da fase.

## Verificação de consistência

O pacote foi comparado, ficheiro a ficheiro, com uma segunda cópia pública e independente, mais
antiga (`kriolos/kriolos-efatura`, versão **2021-12-19**). Todas as diferenças são explicadas pelas
entradas do changelog do próprio pacote (`Read Me.txt`, entradas 2022-01-27 a 2024-05-27) ou são
apenas espaços/CRLF, à exceção de uma edição local do kriolos em `ETSI_XAdESv141.xsd`
(`ArchiveTimeStamp` comentado como "duplicado"), que não é da DNRE. Conclusão: a cópia Kowts é uma reprodução fiel do
pacote 2024-05-27. Isto **não** prova que 2024-05-27 continua a ser a versão em vigor.

Nenhum ficheiro do kriolos foi vendorizado (é a versão antiga e tem edições locais).

## Posição sobre a licença

- A licença MIT do repositório Kowts ("Copyright (c) 2026 Kowts") cobre o código da Kowts, não os
  ficheiros da DNRE.
- O `NOTICE` da Kowts trata os XSD como artefactos oficiais da DNRE, publicados em efatura.cv,
  "redistribuídos apenas para validação local", e afirma que a sua inclusão não implica aprovação
  da DNRE.
- O uso no LexCV é o mesmo: validação local e geração de código, num repositório privado.
- Os termos da DNRE sobre estes ficheiros **não foram verificados** (suposição A1 da pesquisa da
  Phase 136). É uma posição de baixo risco e defensável, mas assumida.

## Substituição pelos ficheiros oficiais

No marco de ligação real à plataforma eFatura, estes ficheiros devem ser substituídos pelos
oficiais descarregados de `efatura.cv`. Antes de substituir, comparar os hashes SHA-256 abaixo com
os dos ficheiros oficiais: se forem iguais, a cópia fica confirmada; se não, rever as diferenças,
atualizar este README, o manifesto em `XsdEfaturaIntegridadeTest` e regenerar o modelo JAXB.

Qualquer alteração a um ficheiro desta pasta faz falhar o build
(`src/test/java/com/lexcv/fiscal/efatura/XsdEfaturaIntegridadeTest.java`). O `.gitattributes` na
raiz marca esta pasta como `-text` para que nenhum checkout (ex. `core.autocrlf` no Windows) altere
os bytes.

## Manifesto SHA-256 (22 XSD, relativo a esta pasta)

```
3366a38ee5632818da0ac830b608e8449aea27e744ea28bc200a68129ffc2fa0  EnvelopedSignature.xsd
b2c669415dede8cf9e6a6c67578db1952c40d064a48d5743e301ec394df40aa6  InternallyDetachedSignature.xsd
6eab59302f2dd0cad0cbba0e5b640a79a8d99e5eed511ed390095f27f05cfe27  common/CV_EFatura_CreditNote_v1.0.xsd
aea1fcea8c214204089bfcfec2e21c23b51bc7c59334662f8213b748ef413dd1  common/CV_EFatura_DebitNote_v1.0.xsd
8ac4cfa0d2e8125335f3fdbb9fd23bf4b1a6897a7aba81f4ead5311d9c61667f  common/CV_EFatura_Elements_v1.0.xsd
d5bdf74d5baad6a7794f7923f5e2f73f70d2d1ce05f1c805f5b51fc63a05c523  common/CV_EFatura_InvoiceReceipt_v1.0.xsd
c2be64f05f2dcc78f5d270ab1620217428935a16958dbc5caa037f1173aa8c01  common/CV_EFatura_Invoice_v1.0.xsd
8c0ef2a8d3fdefa78b3e55234fa991a7398d434df10f3e0aae3d1be2bb63f15d  common/CV_EFatura_MainElements_v1.0.xsd
e1ac68ae79d36c13a0354f7b405e64841060a7eeeec5da4478b04569078d0fb0  common/CV_EFatura_MainTypes_v1.0.xsd
6ce312ba730ec76ade4c8c2c5a8d84b54409b69f0f3fa6e5628a3853a85a7afd  common/CV_EFatura_Receipt_v1.0.xsd
7f8435b918d5577017302f016dd1e73d1af25bff8642f84b33f8bc26e87f1ce1  common/CV_EFatura_RegistrationNote_v1.0.xsd
9b0886b5770745f66ca4e4da99ce2eac1e6beb84fdbbbdd69b5da766f01ba48b  common/CV_EFatura_ReturnNote_v1.0.xsd
aa2231dd32ad2aec44627eb48ac35e7fbfc17ed8f32057a6ddd06ab6bb9787ec  common/CV_EFatura_SalesReceipt_v1.0.xsd
188c74e95e166159d2ec8e8cd74f568dcaed6f5bb64ba3639d78448aefd6cadf  common/CV_EFatura_TaxExemptionReason_v1.0.xsd
9154f0286f5c79851c0fe7a9ff2d52302aa3218a345ba63e35fc51ff0b7be57a  common/CV_EFatura_Transport_v1.0.xsd
c0f34a7115f48d395566a05b7331194def4940df3e4374cb029a611d7975f487  common/CV_EFatura_Types_v1.0.xsd
dcec1fae271c8b1d7a92acc79cf3504e6fab0aa235d8cb2f4d412e889809660b  common/ETSI_XAdESv132.xsd
b202675d8ef478ea74ed1f4bde9b2d661236d9dfb7546d9c7fd7c7894c8d0630  common/ETSI_XAdESv141.xsd
1f19cb4196d9a4b533de3f1df7cd317cdd6af38fdfe98297b8a929a5018197d2  common/ISO_ISO3AlphaCurrencyCode_2012-08-31.xsd
e0d1162144f1126d292ec5adcc3f3fbb83398e527a00e8f3bcdfd8dbb1e0d462  common/ISO_ISOTwo-letterCountryCode_SecondEdition2006.xsd
56b1100a7f14d1d68abd73108d12947c62a0b44ad44eefa9ba1a33e454c786ae  common/UNECE_PaymentMeansCode_D19B.xsd
b4716e1d9ad185b43bdc3464aa584fa5a24b33617361d73240d79858a550474c  common/W3C_XMLDSig.xsd
```

## Documentação e exemplos do pacote (só em teste)

A documentação e os XML de exemplo oficiais do mesmo pacote estão em
`backend/src/test/resources/efatura/2024-05-27/` (fora do classpath de produção):

```
6f31ac6a67cc77fb2ec90d6079ff7d9ed6ab3bf35bdb39d8154824f8e1be4492  Read Me.txt
95a1aede7d29b25afc04d36a98241b1e1057a2dc150ea573ac0e0914116b48ba  XML Fields Map.txt
667e592d33e66f3bdcfd983ea94712705327299476e32ec6b0b05337fc63832b  2 InvoiceReceipt.xml
3b6a080fdb8af5fe45c27248c587364a1a1aa1742b627c703501e38c8ea00b13  5 CreditNote.xml
```
