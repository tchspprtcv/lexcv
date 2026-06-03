import React from 'react';

import styles from './index.module.scss';

const Component = () => {
  return (
    <div className={styles.gestODeClientesLexCv}>
      <div className={styles.mainContentShell}>
        <div className={styles.headerTopAppBarImple}>
          <div className={styles.background}>
            <div className={styles.container}>
              <p className={styles.text}>LexCV</p>
              <div className={styles.verticalDivider} />
              <p className={styles.text2}>Tribunal da Comarca da Praia</p>
            </div>
            <div className={styles.container9}>
              <div className={styles.input}>
                <p className={styles.pesquisaGlobal}>Pesquisa global...</p>
                <div className={styles.container3}>
                  <img
                    src="../image/mpnebdz8-5hphzui.svg"
                    className={styles.container2}
                  />
                </div>
              </div>
              <div className={styles.button}>
                <img
                  src="../image/mpnebdz8-butn0ka.svg"
                  className={styles.container4}
                />
                <div className={styles.backgroundBorder} />
              </div>
              <div className={styles.button2}>
                <img
                  src="../image/mpnebdz8-mcug9d8.svg"
                  className={styles.container5}
                />
              </div>
              <div className={styles.container8}>
                <div className={styles.container7}>
                  <p className={styles.text3}>Dr. Ricardo Silva</p>
                  <div className={styles.container6}>
                    <p className={styles.text4}>Juiz de Direito</p>
                  </div>
                </div>
                <div className={styles.userProfileAvatar} />
              </div>
            </div>
          </div>
        </div>
        <div className={styles.mainContentCanvas}>
          <div className={styles.pageHeader}>
            <div className={styles.container11}>
              <p className={styles.text5}>Módulo de Clientes</p>
              <div className={styles.nav}>
                <p className={styles.text4}>LexCV</p>
                <img
                  src="../image/mpnebdz7-u2jiev2.svg"
                  className={styles.container10}
                />
                <p className={styles.text6}>Gestão de Clientes</p>
              </div>
            </div>
            <div className={styles.button3}>
              <div className={styles.buttonShadow}>
                <img
                  src="../image/mpnebdz7-p9pz2av.svg"
                  className={styles.container12}
                />
                <p className={styles.text7}>Adicionar Novo Cliente</p>
              </div>
            </div>
          </div>
          <div className={styles.bentoStatsGrid}>
            <div className={styles.backgroundBorderShad}>
              <p className={styles.totalDeClientes}>Total de Clientes</p>
              <div className={styles.container15}>
                <p className={styles.text8}>1,284</p>
                <div className={styles.container14}>
                  <img
                    src="../image/mpnebdz7-a08d08s.svg"
                    className={styles.container13}
                  />
                  <p className={styles.text9}>12%</p>
                </div>
              </div>
            </div>
            <div className={styles.backgroundBorderShad2}>
              <p className={styles.totalDeClientes}>Pessoas Singulares</p>
              <p className={styles.a842}>842</p>
            </div>
            <div className={styles.backgroundBorderShad2}>
              <p className={styles.totalDeClientes}>Entidades Coletivas</p>
              <p className={styles.a842}>442</p>
            </div>
            <div className={styles.backgroundBorderShad3}>
              <p className={styles.totalDeClientes}>Processos Ativos</p>
              <p className={styles.a3120}>3,120</p>
            </div>
          </div>
          <div className={styles.container23}>
            <div className={styles.container18}>
              <p className={styles.pesquisarPorNome}>Pesquisar por Nome</p>
              <div className={styles.input2}>
                <p className={styles.pesquisaGlobal}>
                  Nome completo ou razão social...
                </p>
                <div className={styles.container17}>
                  <img
                    src="../image/mpnebdz7-jfmcpra.svg"
                    className={styles.container16}
                  />
                </div>
              </div>
            </div>
            <div className={styles.container21}>
              <p className={styles.pesquisarPorNome}>Filtrar por NIF</p>
              <div className={styles.input3}>
                <p className={styles.pesquisaGlobal}>Ex: 123456789</p>
                <div className={styles.container20}>
                  <img
                    src="../image/mpnebdz7-38jt6fx.svg"
                    className={styles.container19}
                  />
                </div>
              </div>
            </div>
            <div className={styles.button4}>
              <img
                src="../image/mpnebdz7-9b8x2pz.svg"
                className={styles.container22}
              />
              <p className={styles.text10}>Filtros Avançados</p>
            </div>
            <div className={styles.button5}>
              <p className={styles.text11}>Limpar</p>
            </div>
          </div>
          <div className={styles.clientsTable}>
            <div className={styles.table}>
              <div className={styles.headerRow}>
                <p className={styles.text12}>Nome / Razão Social</p>
                <p className={styles.text13}>Tipo</p>
                <p className={styles.text14}>NIF</p>
                <p className={styles.text15}>Contacto</p>
                <p className={styles.text16}>Ações</p>
              </div>
              <div className={styles.body}>
                <div className={styles.row1}>
                  <div className={styles.data}>
                    <div className={styles.overlay}>
                      <p className={styles.text17}>JS</p>
                    </div>
                    <div className={styles.container24}>
                      <p className={styles.text18}>João dos Santos Silva</p>
                      <p className={styles.text19}>ID: #C-10024</p>
                    </div>
                  </div>
                  <div className={styles.data2}>
                    <div className={styles.backgroundBorder2}>
                      <p className={styles.text20}>SINGULAR</p>
                    </div>
                  </div>
                  <p className={styles.text21}>243 129 008</p>
                  <div className={styles.data3}>
                    <p className={styles.a2389876543}>+238 987 65 43</p>
                    <p className={styles.joaoSantosEmailCv}>joao.santos@email.cv</p>
                  </div>
                  <div className={styles.data4}>
                    <div className={styles.button6}>
                      <img
                        src="../image/mpnebdz7-okb94x6.svg"
                        className={styles.container25}
                      />
                    </div>
                    <div className={styles.button7}>
                      <img
                        src="../image/mpnebdz8-2pfrxw1.svg"
                        className={styles.container2}
                      />
                    </div>
                    <div className={styles.button8}>
                      <img
                        src="../image/mpnebdz8-c5j58bq.svg"
                        className={styles.container26}
                      />
                    </div>
                  </div>
                </div>
                <div className={styles.row2}>
                  <div className={styles.data5}>
                    <div className={styles.overlay2}>
                      <p className={styles.text22}>CV</p>
                    </div>
                    <div className={styles.container27}>
                      <p className={styles.text23}>
                        Cape Verde Tech Solutions,
                        <br />
                        SA
                      </p>
                      <p className={styles.text19}>ID: #C-10058</p>
                    </div>
                  </div>
                  <div className={styles.data6}>
                    <div className={styles.backgroundBorder3}>
                      <p className={styles.text24}>COLETIVO</p>
                    </div>
                  </div>
                  <div className={styles.data7}>
                    <p className={styles.text25}>
                      500 293
                      <br />
                      847
                    </p>
                  </div>
                  <div className={styles.data3}>
                    <p className={styles.a2389876543}>+238 261 44 00</p>
                    <p className={styles.joaoSantosEmailCv}>geral@cvtech.cv</p>
                  </div>
                  <div className={styles.data4}>
                    <div className={styles.button6}>
                      <img
                        src="../image/mpnebdz7-okb94x6.svg"
                        className={styles.container25}
                      />
                    </div>
                    <div className={styles.button7}>
                      <img
                        src="../image/mpnebdz8-2pfrxw1.svg"
                        className={styles.container2}
                      />
                    </div>
                    <div className={styles.button8}>
                      <img
                        src="../image/mpnebdz8-c5j58bq.svg"
                        className={styles.container26}
                      />
                    </div>
                  </div>
                </div>
                <div className={styles.row3}>
                  <div className={styles.data}>
                    <div className={styles.overlay}>
                      <p className={styles.text17}>MA</p>
                    </div>
                    <div className={styles.container24}>
                      <p className={styles.text18}>Maria Antónia Lopes</p>
                      <p className={styles.text19}>ID: #C-10062</p>
                    </div>
                  </div>
                  <div className={styles.data2}>
                    <div className={styles.backgroundBorder2}>
                      <p className={styles.text20}>SINGULAR</p>
                    </div>
                  </div>
                  <p className={styles.text26}>199 443 210</p>
                  <div className={styles.data3}>
                    <p className={styles.a2389876543}>+238 991 12 34</p>
                    <p className={styles.joaoSantosEmailCv}>ma.lopes@gmail.com</p>
                  </div>
                  <div className={styles.data4}>
                    <div className={styles.button6}>
                      <img
                        src="../image/mpnebdz7-okb94x6.svg"
                        className={styles.container25}
                      />
                    </div>
                    <div className={styles.button7}>
                      <img
                        src="../image/mpnebdz8-2pfrxw1.svg"
                        className={styles.container2}
                      />
                    </div>
                    <div className={styles.button8}>
                      <img
                        src="../image/mpnebdz8-c5j58bq.svg"
                        className={styles.container26}
                      />
                    </div>
                  </div>
                </div>
                <div className={styles.row4}>
                  <div className={styles.data8}>
                    <div className={styles.overlay3}>
                      <p className={styles.text22}>BC</p>
                    </div>
                    <div className={styles.container24}>
                      <p className={styles.text18}>Banco de Cabo Verde</p>
                      <p className={styles.text19}>ID: #C-10099</p>
                    </div>
                  </div>
                  <div className={styles.data9}>
                    <div className={styles.backgroundBorder3}>
                      <p className={styles.text24}>COLETIVO</p>
                    </div>
                  </div>
                  <p className={styles.text21}>500 000 001</p>
                  <div className={styles.data3}>
                    <p className={styles.a2389876543}>+238 260 41 00</p>
                    <p className={styles.joaoSantosEmailCv}>institucional@bcv.cv</p>
                  </div>
                  <div className={styles.data4}>
                    <div className={styles.button6}>
                      <img
                        src="../image/mpnebdz7-okb94x6.svg"
                        className={styles.container25}
                      />
                    </div>
                    <div className={styles.button7}>
                      <img
                        src="../image/mpnebdz8-2pfrxw1.svg"
                        className={styles.container2}
                      />
                    </div>
                    <div className={styles.button8}>
                      <img
                        src="../image/mpnebdz8-c5j58bq.svg"
                        className={styles.container26}
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div className={styles.pagination}>
              <p className={styles.text27}>A mostrar 1-4 de 1,284 clientes</p>
              <div className={styles.container29}>
                <div className={styles.button9}>
                  <img
                    src="../image/mpnebdz8-biomy7p.svg"
                    className={styles.container28}
                  />
                </div>
                <div className={styles.button10}>
                  <p className={styles.text28}>1</p>
                </div>
                <div className={styles.button11}>
                  <p className={styles.text29}>2</p>
                </div>
                <div className={styles.button11}>
                  <p className={styles.text29}>3</p>
                </div>
                <p className={styles.text30}>...</p>
                <div className={styles.button11}>
                  <p className={styles.text29}>321</p>
                </div>
                <div className={styles.button12}>
                  <img
                    src="../image/mpnebdz8-2e3iknt.svg"
                    className={styles.container28}
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
        <div className={styles.container32}>
          <div className={styles.container30}>
            <img
              src="../image/mpnebe0j-v0jsyff.png"
              className={styles.nOSiLogoPlaceholder}
            />
            <p className={styles.text4}>
              © 2024 LexCV - Sistema Judicial de Cabo Verde. Desenvolvido por NOSi.
            </p>
          </div>
          <div className={styles.container31}>
            <p className={styles.pesquisarPorNome}>Termos de Uso</p>
            <p className={styles.pesquisarPorNome}>Privacidade</p>
            <p className={styles.pesquisarPorNome}>Acessibilidade</p>
          </div>
        </div>
      </div>
      <div className={styles.asideSideNavBarImple}>
        <div className={styles.container33}>
          <p className={styles.lexCv}>LexCV</p>
          <p className={styles.sistemaJudicialCaboV}>Sistema Judicial Cabo Verde</p>
        </div>
        <div className={styles.nav2}>
          <div className={styles.link}>
            <img
              src="../image/mpnebdz8-a7ae39e.svg"
              className={styles.container34}
            />
            <p className={styles.text31}>Dashboard</p>
          </div>
          <div className={styles.linkActiveStateClien}>
            <img
              src="../image/mpnebdz8-1xolcwf.svg"
              className={styles.container35}
            />
            <p className={styles.text32}>Clientes</p>
          </div>
          <div className={styles.link2}>
            <img
              src="../image/mpnebdz8-e039flf.svg"
              className={styles.container36}
            />
            <p className={styles.text31}>Processos</p>
          </div>
          <div className={styles.link3}>
            <img
              src="../image/mpnebdz8-44mdrtu.svg"
              className={styles.container37}
            />
            <p className={styles.text31}>Agenda</p>
          </div>
          <div className={styles.link4}>
            <img
              src="../image/mpnebdz8-5s4whsu.svg"
              className={styles.container4}
            />
            <p className={styles.text31}>Documentos</p>
          </div>
          <div className={styles.link5}>
            <img
              src="../image/mpnebdz8-dditzio.svg"
              className={styles.container35}
            />
            <p className={styles.text31}>Financeiro</p>
          </div>
        </div>
        <div className={styles.horizontalBorder}>
          <div className={styles.link2}>
            <img
              src="../image/mpnebdz8-53lqxll.svg"
              className={styles.container36}
            />
            <p className={styles.text31}>Configurações</p>
          </div>
          <div className={styles.link2}>
            <img
              src="../image/mpnebdz8-y7k6juj.svg"
              className={styles.container36}
            />
            <p className={styles.text31}>Suporte</p>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Component;
