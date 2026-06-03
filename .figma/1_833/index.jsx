import React from 'react';

import styles from './index.module.scss';

const Component = () => {
  return (
    <div className={styles.dashboardLexCv}>
      <div className={styles.asideSideNavBar}>
        <div className={styles.container}>
          <p className={styles.lexCv}>LexCV</p>
          <p className={styles.sIstemajudicialcabov}>SISTEMA JUDICIAL CABO VERDE</p>
        </div>
        <div className={styles.nav}>
          <div className={styles.linkDashboardActive}>
            <img
              src="../image/mpnebxxf-u5azn5d.svg"
              className={styles.container2}
            />
            <p className={styles.text}>Dashboard</p>
          </div>
          <div className={styles.link}>
            <img
              src="../image/mpnebxxf-qp9rsbg.svg"
              className={styles.container3}
            />
            <p className={styles.text2}>Clientes</p>
          </div>
          <div className={styles.link2}>
            <img
              src="../image/mpnebxxf-ew9zgrl.svg"
              className={styles.container4}
            />
            <p className={styles.text2}>Processos</p>
          </div>
          <div className={styles.link3}>
            <img
              src="../image/mpnebxxf-gzqti21.svg"
              className={styles.container5}
            />
            <p className={styles.text2}>Agenda</p>
          </div>
          <div className={styles.link4}>
            <img
              src="../image/mpnebxxf-jycx1ag.svg"
              className={styles.container6}
            />
            <p className={styles.text2}>Documentos</p>
          </div>
          <div className={styles.link5}>
            <img
              src="../image/mpnebxxf-5qvz7hy.svg"
              className={styles.container3}
            />
            <p className={styles.text2}>Financeiro</p>
          </div>
        </div>
        <div className={styles.container8}>
          <div className={styles.link2}>
            <img
              src="../image/mpnebxxf-i5ml5mr.svg"
              className={styles.container4}
            />
            <p className={styles.text2}>Configurações</p>
          </div>
          <div className={styles.link2}>
            <img
              src="../image/mpnebxxf-hwmfmq5.svg"
              className={styles.container4}
            />
            <p className={styles.text2}>Suporte</p>
          </div>
          <div className={styles.horizontalBorder}>
            <img
              src="../image/mpneby03-tyqycx1.png"
              className={styles.userAvatar}
            />
            <div className={styles.container7}>
              <p className={styles.text3}>Dr. Arnaldo Silva</p>
              <p className={styles.text4}>ADVOGADO SÉNIOR</p>
            </div>
          </div>
        </div>
      </div>
      <div className={styles.mainContentArea}>
        <div className={styles.headerTopAppBar}>
          <div className={styles.input}>
            <p className={styles.pesquisarProcessosCp}>
              Pesquisar processos, CPFs, nomes...
            </p>
            <img src="../image/mpnebxxd-cqsdrqn.svg" className={styles.icon} />
          </div>
          <div className={styles.container13}>
            <div className={styles.container10}>
              <img
                src="../image/mpnebxxd-jjumlxr.svg"
                className={styles.container9}
              />
              <p className={styles.text5}>Tribunal da Comarca da Praia</p>
            </div>
            <div className={styles.container12}>
              <div className={styles.button}>
                <img
                  src="../image/mpnebxxd-69l0thh.svg"
                  className={styles.container6}
                />
                <div className={styles.backgroundBorder} />
              </div>
              <div className={styles.button2}>
                <img
                  src="../image/mpnebxxd-ro3m7z3.svg"
                  className={styles.container11}
                />
              </div>
            </div>
          </div>
        </div>
        <div className={styles.canvas}>
          <div className={styles.pageHeader}>
            <div className={styles.container14}>
              <p className={styles.text6}>Dashboard Institucional</p>
              <p className={styles.text7}>
                Bem-vindo de volta, Dr. Arnaldo. Aqui está o resumo da sua atividade
                judicial hoje.
              </p>
            </div>
            <div className={styles.button3}>
              <img
                src="../image/mpnebxxd-4bzsmw0.svg"
                className={styles.container15}
              />
              <p className={styles.text8}>Novo Processo</p>
            </div>
          </div>
          <div className={styles.bentoGridSummaryCard}>
            <div className={styles.cardClientesAtivos}>
              <div className={styles.container18}>
                <div className={styles.overlay}>
                  <img
                    src="../image/mpnebxxd-6kvshm2.svg"
                    className={styles.container11}
                  />
                </div>
                <div className={styles.container17}>
                  <img
                    src="../image/mpnebxxd-7wv2c5b.svg"
                    className={styles.container16}
                  />
                  <p className={styles.text9}>12%</p>
                </div>
              </div>
              <p className={styles.clientesAtivos}>Clientes Ativos</p>
              <p className={styles.a128}>128</p>
            </div>
            <div className={styles.cardProcessosEmCurso}>
              <div className={styles.container20}>
                <div className={styles.overlay2}>
                  <img
                    src="../image/mpnebxxd-c7lr7w6.svg"
                    className={styles.container19}
                  />
                </div>
                <p className={styles.text10}>Estável</p>
              </div>
              <p className={styles.clientesAtivos}>Processos em Curso</p>
              <p className={styles.a128}>342</p>
            </div>
            <div className={styles.cardPrazosPrXimos}>
              <div className={styles.container22}>
                <div className={styles.overlay3}>
                  <img
                    src="../image/mpnebxxd-cxcr184.svg"
                    className={styles.container21}
                  />
                </div>
                <div className={styles.overlay4}>
                  <p className={styles.text11}>Urgente</p>
                </div>
              </div>
              <p className={styles.clientesAtivos}>Prazos Próximos</p>
              <p className={styles.a14}>14</p>
            </div>
            <div className={styles.cardRendimentoMensal}>
              <div className={styles.container24}>
                <div className={styles.overlay5}>
                  <img
                    src="../image/mpnebxxd-0b6o5l3.svg"
                    className={styles.container23}
                  />
                </div>
                <p className={styles.text9}>+8%</p>
              </div>
              <p className={styles.clientesAtivos}>Honorários/Mês</p>
              <div className={styles.heading3}>
                <p className={styles.text12}>845k&nbsp;</p>
                <p className={styles.text13}>CVE</p>
              </div>
            </div>
          </div>
          <div className={styles.dashboardChartsActiv}>
            <div className={styles.mainContentColumn}>
              <div className={styles.processChartSimulati}>
                <div className={styles.container25}>
                  <p className={styles.text14}>Status dos Processos</p>
                  <div className={styles.imageClip}>
                    <img
                      src="../image/mpnebxxd-cneec0w.svg"
                      className={styles.image}
                    />
                    <p className={styles.text15}>Últimos 6 meses</p>
                  </div>
                </div>
                <div className={styles.container26}>
                  <p className={styles.text16}>Novos</p>
                  <p className={styles.text17}>Prazos</p>
                  <p className={styles.text16}>Ativos</p>
                  <p className={styles.text18}>Concluídos</p>
                  <p className={styles.text18}>Arquivados</p>
                </div>
              </div>
              <div className={styles.recentTable}>
                <div className={styles.horizontalBorder2}>
                  <p className={styles.text14}>Processos Recentes</p>
                  <p className={styles.text19}>Ver todos</p>
                </div>
                <div className={styles.table}>
                  <div className={styles.row}>
                    <div className={styles.cell}>
                      <p className={styles.text20}>
                        ID
                        <br />
                        PROCESSO
                      </p>
                    </div>
                    <p className={styles.text21}>CLIENTE</p>
                    <p className={styles.text22}>TRIBUNAL</p>
                    <p className={styles.text23}>ESTADO</p>
                    <p className={styles.text24}>AÇÃO</p>
                  </div>
                  <div className={styles.body}>
                    <div className={styles.row2}>
                      <div className={styles.data}>
                        <p className={styles.text25}>
                          CV-2023-
                          <br />
                          00452
                        </p>
                      </div>
                      <div className={styles.data2}>
                        <p className={styles.text26}>
                          Enacol
                          <br />
                          S.A.
                        </p>
                      </div>
                      <div className={styles.data3}>
                        <p className={styles.text27}>
                          Cível -<br />
                          Praia
                        </p>
                      </div>
                      <div className={styles.data4}>
                        <div className={styles.overlay6}>
                          <p className={styles.text28}>
                            Em
                            <br />
                            Julgamento
                          </p>
                        </div>
                      </div>
                      <div className={styles.data5}>
                        <img
                          src="../image/mpnebxxe-pmf6x86.svg"
                          className={styles.button4}
                        />
                      </div>
                    </div>
                    <div className={styles.row3}>
                      <div className={styles.data6}>
                        <p className={styles.text25}>
                          CV-2023-
                          <br />
                          00811
                        </p>
                      </div>
                      <div className={styles.data7}>
                        <p className={styles.text29}>
                          Imobiliária
                          <br />
                          Turística
                        </p>
                      </div>
                      <div className={styles.data8}>
                        <p className={styles.text30}>
                          Laboral -<br />
                          S.Vicente
                        </p>
                      </div>
                      <div className={styles.data9}>
                        <div className={styles.overlay7}>
                          <p className={styles.text31}>
                            Aguardando
                            <br />
                            Réu
                          </p>
                        </div>
                      </div>
                      <div className={styles.data10}>
                        <img
                          src="../image/mpnebxxe-pmf6x86.svg"
                          className={styles.button4}
                        />
                      </div>
                    </div>
                    <div className={styles.row4}>
                      <div className={styles.data}>
                        <p className={styles.text25}>
                          CV-2024-
                          <br />
                          00120
                        </p>
                      </div>
                      <div className={styles.data11}>
                        <p className={styles.text32}>
                          João B.
                          <br />
                          Rodrigues
                        </p>
                      </div>
                      <div className={styles.data12}>
                        <p className={styles.text33}>
                          Crime -<br />
                          Assomada
                        </p>
                      </div>
                      <div className={styles.data13}>
                        <div className={styles.overlay8}>
                          <p className={styles.text34}>Novo</p>
                        </div>
                      </div>
                      <div className={styles.data14}>
                        <img
                          src="../image/mpnebxxe-pmf6x86.svg"
                          className={styles.button4}
                        />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div className={styles.activitySidebarColum}>
              <div className={styles.urgentDeadlinesCard}>
                <div className={styles.heading4}>
                  <img
                    src="../image/mpnebxxf-g27htsk.svg"
                    className={styles.container27}
                  />
                  <p className={styles.text35}>Prazos Urgentes</p>
                </div>
                <div className={styles.container31}>
                  <div className={styles.backgroundVerticalBo}>
                    <div className={styles.paragraph}>
                      <p className={styles.text36}>Réplica Contestação</p>
                      <p className={styles.text37}>HOJE</p>
                    </div>
                    <p className={styles.processoN1242023Trib}>
                      Processo nº 124/2023 -<br />
                      Tribunal da Praia
                    </p>
                    <div className={styles.container28}>
                      <img
                        src="../image/mpnebxxf-a1x8cy3.svg"
                        className={styles.container15}
                      />
                      <p className={styles.text38}>Até às 16:00</p>
                    </div>
                  </div>
                  <div className={styles.backgroundVerticalBo2}>
                    <div className={styles.paragraph2}>
                      <p className={styles.text36}>Pagamento de Custas</p>
                      <p className={styles.text39}>AMANHÃ</p>
                    </div>
                    <p className={styles.clienteCarlosManuelL}>
                      Cliente: Carlos Manuel Lopes
                    </p>
                    <div className={styles.container30}>
                      <img
                        src="../image/mpnebxxf-x6crjzu.svg"
                        className={styles.container29}
                      />
                      <p className={styles.text38}>24 de Outubro</p>
                    </div>
                  </div>
                </div>
                <div className={styles.button5}>
                  <p className={styles.text5}>Ver Agenda Completa</p>
                </div>
              </div>
              <div className={styles.recentActivityFeed}>
                <p className={styles.atividadeRecente}>Atividade Recente</p>
                <div className={styles.container35}>
                  <div className={styles.verticalDivider} />
                  <div className={styles.activityItem1}>
                    <p className={styles.documentoSubmetidoCo}>
                      Documento Submetido:
                      <br />
                      Contestação no processo CV-
                      <br />
                      00452.
                    </p>
                    <p className={styles.h45Minutos}>HÁ 45 MINUTOS</p>
                    <div className={styles.background}>
                      <div className={styles.overlayShadow}>
                        <img
                          src="../image/mpnebxxf-sd3mf9m.svg"
                          className={styles.container32}
                        />
                      </div>
                    </div>
                  </div>
                  <div className={styles.activityItem2}>
                    <p className={styles.documentoSubmetidoCo}>
                      Novo Cliente: Maria dos Santos
                      <br />
                      registada no sistema.
                    </p>
                    <p className={styles.h45Minutos}>HÁ 2 HORAS</p>
                    <div className={styles.background2}>
                      <div className={styles.overlayShadow2}>
                        <img
                          src="../image/mpnebxxf-fem8375.svg"
                          className={styles.container33}
                        />
                      </div>
                    </div>
                  </div>
                  <div className={styles.activityItem3}>
                    <p className={styles.documentoSubmetidoCo}>
                      Processo Concluído: Acordo
                      <br />
                      extrajudicial assinado em
                      <br />
                      0092/23.
                    </p>
                    <p className={styles.h45Minutos}>ONTEM, 17:40</p>
                    <div className={styles.background3}>
                      <div className={styles.overlayShadow3}>
                        <img
                          src="../image/mpnebxxf-sdkubyx.svg"
                          className={styles.container34}
                        />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div className={styles.buttonFabForQuickAct2}>
            <div className={styles.buttonFabForQuickAct}>
              <img
                src="../image/mpnebxxg-ze5kr3n.svg"
                className={styles.container36}
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Component;
