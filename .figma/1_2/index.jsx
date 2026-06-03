import React from 'react';

import styles from './index.module.scss';

const Component = () => {
  return (
    <div className={styles.agendaEPrazosLexCv}>
      <div className={styles.asideSideNavBar}>
        <div className={styles.container2}>
          <div className={styles.container}>
            <p className={styles.sistemaJudicialCaboV}>
              Sistema Judicial Cabo Verde
            </p>
          </div>
        </div>
        <div className={styles.nav}>
          <div className={styles.link}>
            <img
              src="../image/mpneb15r-f4kbixu.svg"
              className={styles.container3}
            />
            <p className={styles.text}>Dashboard</p>
          </div>
          <div className={styles.link2}>
            <img
              src="../image/mpneb15r-o8px35v.svg"
              className={styles.container4}
            />
            <p className={styles.text}>Clientes</p>
          </div>
          <div className={styles.link3}>
            <img
              src="../image/mpneb15r-7oa5ehe.svg"
              className={styles.container5}
            />
            <p className={styles.text}>Processos</p>
          </div>
          <div className={styles.linkActiveItem}>
            <img
              src="../image/mpneb15r-hckw5n9.svg"
              className={styles.container6}
            />
            <p className={styles.text2}>Agenda</p>
          </div>
          <div className={styles.link4}>
            <img
              src="../image/mpneb15r-npao9dw.svg"
              className={styles.container7}
            />
            <p className={styles.text}>Documentos</p>
          </div>
          <div className={styles.link2}>
            <img
              src="../image/mpneb15s-3no64eh.svg"
              className={styles.container4}
            />
            <p className={styles.text}>Financeiro</p>
          </div>
        </div>
        <div className={styles.horizontalBorder}>
          <div className={styles.link3}>
            <img
              src="../image/mpneb15s-7as6s42.svg"
              className={styles.container5}
            />
            <p className={styles.text}>Configurações</p>
          </div>
          <div className={styles.link3}>
            <img
              src="../image/mpneb15s-0b5yjff.svg"
              className={styles.container5}
            />
            <p className={styles.text}>Suporte</p>
          </div>
        </div>
      </div>
      <div className={styles.headerTopAppBar}>
        <div className={styles.container9}>
          <p className={styles.text3}>LexCV</p>
          <div className={styles.input}>
            <p className={styles.pesquisarProcessosPr}>
              Pesquisar processos, prazos...
            </p>
            <img
              src="../image/mpneb15s-kh3whxb.svg"
              className={styles.container8}
            />
          </div>
        </div>
        <div className={styles.container11}>
          <p className={styles.text4}>Tribunal da Comarca da Praia</p>
          <div className={styles.verticalBorder}>
            <div className={styles.button}>
              <img
                src="../image/mpneb15s-oxoak3a.svg"
                className={styles.container7}
              />
            </div>
            <div className={styles.button2}>
              <img
                src="../image/mpneb15s-5pj71nw.svg"
                className={styles.container10}
              />
            </div>
            <div className={styles.background}>
              <img
                src="../image/mpneb16v-utlnd5s.png"
                className={styles.userProfileAvatar}
              />
            </div>
          </div>
        </div>
      </div>
      <div className={styles.container29}>
        <div className={styles.container28}>
          <div className={styles.calendarSection}>
            <div className={styles.container15}>
              <div className={styles.container12}>
                <p className={styles.text5}>Maio 2024</p>
                <p className={styles.text6}>
                  Gestão de prazos e audiências
                  <br />
                  institucionais
                </p>
              </div>
              <div className={styles.backgroundBorder}>
                <div className={styles.button3}>
                  <img
                    src="../image/mpneb15s-di84o80.svg"
                    className={styles.container13}
                  />
                </div>
                <p className={styles.text7}>Hoje</p>
                <div className={styles.button3}>
                  <img
                    src="../image/mpneb15s-54d4g3y.svg"
                    className={styles.container13}
                  />
                </div>
              </div>
              <div className={styles.button4}>
                <img
                  src="../image/mpneb15s-pal6cml.svg"
                  className={styles.container14}
                />
                <p className={styles.text8}>
                  Novo
                  <br />
                  Evento
                </p>
              </div>
            </div>
            <div className={styles.calendarControlsLege}>
              <div className={styles.backgroundBorder2}>
                <div className={styles.background2} />
                <p className={styles.text9}>Prazos Fatais</p>
              </div>
              <div className={styles.backgroundBorder3}>
                <div className={styles.background3} />
                <p className={styles.text9}>Audiências</p>
              </div>
              <div className={styles.backgroundBorder4}>
                <div className={styles.background4} />
                <p className={styles.text9}>Diligências</p>
              </div>
              <div className={styles.backgroundBorder5}>
                <div className={styles.background5} />
                <p className={styles.text9}>Reuniões</p>
              </div>
            </div>
            <div className={styles.calendarGrid}>
              <div className={styles.backgroundHorizontal}>
                <div className={styles.autoWrapper}>
                  <p className={styles.text10}>Dom</p>
                  <p className={styles.text11}>Seg</p>
                  <p className={styles.text12}>Ter</p>
                </div>
                <div className={styles.autoWrapper2}>
                  <p className={styles.text10}>Qua</p>
                  <p className={styles.text11}>Qui</p>
                </div>
                <p className={styles.text13}>Sex</p>
                <p className={styles.text14}>Sáb</p>
              </div>
              <div className={styles.container17}>
                <div className={styles.autoWrapper5}>
                  <div className={styles.autoWrapper3}>
                    <div className={styles.placeholderDaysForEn}>
                      <p className={styles.text15}>28</p>
                    </div>
                    <div className={styles.overlayBorder}>
                      <p className={styles.text15}>29</p>
                    </div>
                    <div className={styles.overlayBorder2}>
                      <p className={styles.text15}>30</p>
                    </div>
                  </div>
                  <div className={styles.autoWrapper4}>
                    <div className={styles.row2}>
                      <p className={styles.text15}>5</p>
                    </div>
                    <div className={styles.border}>
                      <p className={styles.text15}>6</p>
                      <div className={styles.overlayBorder3}>
                        <p className={styles.text16}>Prazo: Recurso STJ</p>
                      </div>
                    </div>
                    <div className={styles.border2}>
                      <p className={styles.text15}>7</p>
                    </div>
                    <div className={styles.remainingRowsImplied}>
                      <p className={styles.text15}>12</p>
                    </div>
                    <div className={styles.verticalBorder2}>
                      <p className={styles.text15}>13</p>
                    </div>
                    <div className={styles.verticalBorder3}>
                      <p className={styles.text15}>14</p>
                    </div>
                  </div>
                </div>
                <div className={styles.autoWrapper7}>
                  <div className={styles.mayDays}>
                    <p className={styles.text15}>1</p>
                    <div className={styles.overlayBorder4}>
                      <p className={styles.text17}>Feriado Nacional</p>
                    </div>
                  </div>
                  <div className={styles.border3}>
                    <p className={styles.text15}>2</p>
                  </div>
                  <div className={styles.border2}>
                    <p className={styles.text15}>9</p>
                  </div>
                  <div className={styles.verticalBorder4}>
                    <p className={styles.text15}>15</p>
                  </div>
                  <div className={styles.verticalBorder5}>
                    <p className={styles.text15}>16</p>
                  </div>
                  <div className={styles.backgroundBorder6}>
                    <div className={styles.overlayShadow}>
                      <div className={styles.autoWrapper6}>
                        <p className={styles.text18}>8</p>
                        <div className={styles.background6} />
                      </div>
                      <div className={styles.container16}>
                        <div className={styles.overlayBorder5}>
                          <p className={styles.text19}>Diligência Local</p>
                        </div>
                        <div className={styles.overlayBorder6}>
                          <p className={styles.text20}>
                            14:00 Audiência Preliminar
                          </p>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <div className={styles.autoWrapper8}>
                  <div className={styles.border4}>
                    <p className={styles.text15}>3</p>
                    <div className={styles.overlayBorder6}>
                      <p className={styles.text20}>Audiência - Proc. 102/24</p>
                    </div>
                  </div>
                  <div className={styles.border5}>
                    <p className={styles.text15}>10</p>
                    <div className={styles.overlayBorder3}>
                      <p className={styles.text16}>Prazo: Contestação</p>
                    </div>
                  </div>
                  <div className={styles.verticalBorder6}>
                    <p className={styles.text15}>17</p>
                  </div>
                </div>
                <div className={styles.autoWrapper9}>
                  <div className={styles.horizontalBorder2}>
                    <p className={styles.text15}>4</p>
                  </div>
                  <div className={styles.horizontalBorder2}>
                    <p className={styles.text15}>11</p>
                  </div>
                  <p className={styles.text21}>18</p>
                </div>
              </div>
            </div>
          </div>
          <div className={styles.asideSidebarUpcoming}>
            <div className={styles.backgroundBorderShad}>
              <div className={styles.container18}>
                <p className={styles.text22}>Próximos Eventos</p>
                <div className={styles.background7}>
                  <p className={styles.text23}>BREVEMENTE</p>
                </div>
              </div>
              <div className={styles.container23}>
                <div className={styles.eventCard}>
                  <div className={styles.paragraph}>
                    <p className={styles.text24}>PRAZO FATAL</p>
                    <p className={styles.text25}>09:00</p>
                  </div>
                  <p className={styles.interposiODeRecursoE}>
                    Interposição de Recurso Especial
                  </p>
                  <p className={styles.procN2242023Stj}>Proc. nº 224/2023 - STJ</p>
                  <div className={styles.container20}>
                    <img
                      src="../image/mpneb15s-50112yl.svg"
                      className={styles.container19}
                    />
                    <p className={styles.text26}>Dr. João Silva</p>
                  </div>
                </div>
                <div className={styles.verticalBorder7}>
                  <div className={styles.paragraph2}>
                    <p className={styles.text27}>AUDIÊNCIA</p>
                    <p className={styles.text25}>14:30</p>
                  </div>
                  <p className={styles.interposiODeRecursoE}>
                    Julgamento e Discussão
                  </p>
                  <p className={styles.procN2242023Stj}>
                    Proc. nº 102/2024 - Comarca Praia
                  </p>
                  <div className={styles.container22}>
                    <img
                      src="../image/mpneb15s-9i8hiqw.svg"
                      className={styles.container21}
                    />
                    <p className={styles.text26}>Sala 3 - Piso 1</p>
                  </div>
                </div>
                <div className={styles.verticalBorder8}>
                  <div className={styles.paragraph3}>
                    <p className={styles.text28}>DILIGÊNCIA</p>
                    <p className={styles.text25}>10:00</p>
                  </div>
                  <p className={styles.interposiODeRecursoE}>
                    Citação de Testemunha
                  </p>
                  <p className={styles.procN2242023Stj}>
                    Proc. nº 045/2024 - Fazenda
                  </p>
                </div>
                <div className={styles.verticalBorder9}>
                  <div className={styles.paragraph4}>
                    <p className={styles.text27}>REUNIÃO</p>
                    <p className={styles.text25}>16:00</p>
                  </div>
                  <p className={styles.interposiODeRecursoE}>
                    Conferência de Advogados
                  </p>
                  <p className={styles.procN2242023Stj}>Gabinete Central</p>
                </div>
              </div>
              <div className={styles.button5}>
                <p className={styles.text29}>Ver Lista Completa</p>
              </div>
            </div>
            <div className={styles.miniStatsCard}>
              <img
                src="../image/mpneb15s-3qp0pbj.svg"
                className={styles.backgroundDecoration}
              />
              <div className={styles.container27}>
                <div className={styles.heading4}>
                  <p className={styles.visOGeralDaSemana}>Visão Geral da Semana</p>
                </div>
                <div className={styles.container24}>
                  <p className={styles.text30}>12</p>
                  <p className={styles.text31}>Prazos ativos</p>
                </div>
                <div className={styles.container26}>
                  <div className={styles.overlayOverlayBlur}>
                    <div className={styles.container25}>
                      <p className={styles.audiNcias}>Audiências</p>
                    </div>
                    <p className={styles.text32}>04</p>
                  </div>
                  <div className={styles.overlayOverlayBlur2}>
                    <div className={styles.container25}>
                      <p className={styles.audiNcias}>Urgentes</p>
                    </div>
                    <p className={styles.text33}>02</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Component;
