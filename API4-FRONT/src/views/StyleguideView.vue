<template>
  <div class="styleguide">
    <header class="header-section">
      <h1>UI Styleguide</h1>
      <p>
        Catálogo oficial de estilos e componentes globais. Use as classes e variáveis abaixo para
        manter a consistência do design.
      </p>
    </header>

    <section>
      <h2>1. Paleta de Cores</h2>
      <p class="section-desc">Variáveis CSS globais configuradas no <code>base.css</code>.</p>

      <h3 class="mt-4 mb-2">Cores da Marca</h3>
      <div class="color-grid">
        <div class="color-box" style="background: var(--vis-brand-orange)">
          <span>--vis-brand-orange<br />#f26522</span>
        </div>
        <div class="color-box" style="background: var(--vis-brand-orange-hover)">
          <span>--vis-brand-orange-hover<br />#d9541a</span>
        </div>
        <div class="color-box" style="background: var(--vis-brand-orange-light)">
          <span>--vis-brand-orange-light<br />#ff8547</span>
        </div>
      </div>

      <h3 class="mt-4 mb-2">Feedback Semântico</h3>
      <div class="color-grid">
        <div class="color-box" style="background: var(--vis-c-success)">
          <span>--vis-c-success</span>
        </div>
        <div class="color-box" style="background: var(--vis-c-danger)">
          <span>--vis-c-danger</span>
        </div>
        <div class="color-box" style="background: var(--vis-c-warning)">
          <span>--vis-c-warning</span>
        </div>
        <div class="color-box" style="background: var(--vis-c-info)">
          <span>--vis-c-info</span>
        </div>
      </div>

      <h3 class="mt-4 mb-2">Tons Escuros (Dark)</h3>
      <div class="color-grid">
        <div class="color-box" style="background: var(--vis-c-dark)">
          <span style="color: white">--vis-c-dark</span>
        </div>
        <div class="color-box" style="background: var(--vis-c-dark-soft)">
          <span style="color: white">--vis-c-dark-soft</span>
        </div>
        <div class="color-box" style="background: var(--vis-c-dark-mute)">
          <span style="color: white">--vis-c-dark-mute</span>
        </div>
      </div>

      <h3 class="mt-4 mb-2">Tons Claros (Light)</h3>
      <div class="color-grid">
        <div
          class="color-box"
          style="background: var(--vis-c-light); border: 1px solid var(--color-border)"
        >
          <span style="color: var(--color-text)">--vis-c-light</span>
        </div>
        <div
          class="color-box"
          style="background: var(--vis-c-light-soft); border: 1px solid var(--color-border)"
        >
          <span style="color: var(--color-text)">--vis-c-light-soft</span>
        </div>
        <div
          class="color-box"
          style="background: var(--vis-c-light-mute); border: 1px solid var(--color-border)"
        >
          <span style="color: var(--color-text)">--vis-c-light-mute</span>
        </div>
      </div>
    </section>

    <section>
      <h2>2. Botões</h2>
      <p class="section-desc">Classes globais: <code>.btn</code> combinado com modificadores.</p>

      <table class="component-table">
        <thead>
          <tr>
            <th>Classe</th>
            <th>Normal</th>
            <th>Desabilitado (disabled)</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>.btn .btn_primary</code></td>
            <td><button class="btn btn_primary">Processar Dados</button></td>
            <td><button class="btn btn_primary" disabled>Processar Dados</button></td>
          </tr>
          <tr>
            <td><code>.btn .btn_success</code></td>
            <td><button class="btn btn_success">Aprovar Lote</button></td>
            <td><button class="btn btn_success" disabled>Aprovar Lote</button></td>
          </tr>
          <tr>
            <td><code>.btn .btn_recuse</code></td>
            <td><button class="btn btn_recuse">Rejeitar Lote</button></td>
            <td><button class="btn btn_recuse" disabled>Rejeitar Lote</button></td>
          </tr>
          <tr>
            <td><code>.btn .btn_outline</code></td>
            <td><button class="btn btn_outline">Ver Detalhes</button></td>
            <td><button class="btn btn_outline" disabled>Ver Detalhes</button></td>
          </tr>
        </tbody>
      </table>
    </section>

    <section>
      <h2>3. Formulários (Inputs)</h2>
      <p class="section-desc">
        Estruturas básicas de formulário usando <code>.label</code> e <code>.input</code>.
      </p>

      <table class="component-table">
        <thead>
          <tr>
            <th>Classe</th>
            <th>Normal</th>
            <th>Desabilitado (disabled)</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>.input</code> (Text)</td>
            <td>
              <label class="label">Label</label>
              <input type="text" class="input" />
            </td>
            <td>
              <label class="label">Label</label>
              <input type="text" class="input" disabled />
            </td>
          </tr>
          <tr>
            <td><code>.input</code> (Select)</td>
            <td>
              <label class="label">Label</label>
              <select class="input">
                <option></option>
              </select>
            </td>
            <td>
              <label class="label">Label</label>
              <select class="input" disabled>
                <option></option>
              </select>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section>
      <h2>4. Badges (Tags / Status)</h2>
      <p class="section-desc">
        Pequenos indicadores usando a classe <code>.badge</code> combinada com modificadores de cor.
      </p>

      <table class="component-table">
        <thead>
          <tr>
            <th>Classe</th>
            <th>Visualização</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>.badge .badge_primary</code></td>
            <td><span class="badge badge_primary">Badge</span></td>
          </tr>
          <tr>
            <td><code>.badge .badge_success</code></td>
            <td><span class="badge badge_success">Badge</span></td>
          </tr>
          <tr>
            <td><code>.badge .badge_warning</code></td>
            <td><span class="badge badge_warning">Badge</span></td>
          </tr>
          <tr>
            <td><code>.badge .badge_danger</code></td>
            <td><span class="badge badge_danger">Badge</span></td>
          </tr>
          <tr>
            <td><code>.badge .badge_info</code></td>
            <td><span class="badge badge_info">Badge</span></td>
          </tr>
        </tbody>
      </table>
    </section>

    <section>
      <h2>5. Checkboxes e Radios</h2>
      <p class="section-desc">
        Inputs de seleção usando as classes <code>.checkbox</code> e <code>.radio</code>.
      </p>

      <table class="component-table">
        <thead>
          <tr>
            <th>Classe</th>
            <th>Normal</th>
            <th>Desabilitado (disabled)</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>.checkbox</code></td>
            <td><input type="checkbox" class="checkbox" checked /></td>
            <td><input type="checkbox" class="checkbox" checked disabled /></td>
          </tr>
          <tr>
            <td><code>.radio</code></td>
            <td><input type="radio" class="radio" checked /></td>
            <td><input type="radio" class="radio" checked disabled /></td>
          </tr>
        </tbody>
      </table>
    </section>

    <section>
      <h2>6. Loading (Spinner)</h2>
      <p class="section-desc">
        Indicador de carregamento simples usando a classe <code>.spinner</code>.
      </p>

      <table class="component-table">
        <thead>
          <tr>
            <th>Classe</th>
            <th>Visualização</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>.spinner</code></td>
            <td><div class="spinner"></div></td>
          </tr>
        </tbody>
      </table>
    </section>

    <section>
      <h2>7. Futuros Componentes Estruturais (Lembrete)</h2>
      <p class="section-desc">
        Os seguintes itens não possuem CSS global direto em <code>base.css</code>, pois serão
        desenvolvidos como <strong>Componentes Vue Isolados</strong> (com CSS próprio / Scoped):
      </p>

      <table class="component-table">
        <thead>
          <tr>
            <th>Componente</th>
            <th>Descrição e Propósito</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><strong>NavBar (Menu Superior)</strong></td>
            <td>
              Componente <code>&lt;AppNavbar /&gt;</code> para navegação principal, logo,
              breadcrumbs e menu de usuário (perfil/logout). Deve ser fixo no topo da aplicação.
            </td>
          </tr>
          <tr>
            <td><strong>SideBar (Menu Lateral)</strong></td>
            <td>
              Componente <code>&lt;AppSidebar /&gt;</code> para menus estruturais, filtros globais
              ou lista de camadas espaciais. Deve permitir ser recolhível (collapsible).
            </td>
          </tr>
          <tr>
            <td><strong>Footer (Rodapé)</strong></td>
            <td>
              Componente <code>&lt;AppFooter /&gt;</code> para exibir links úteis, versão do
              sistema, status da API e termos de uso.
            </td>
          </tr>
          <tr>
            <td><strong>Data Table (Tabela de Dados)</strong></td>
            <td>
              Componente <code>&lt;DataTable /&gt;</code> reutilizável para listar bases, registros
              de quarentena e logs, com suporte a ordenação, busca e botões de ação nas linhas.
            </td>
          </tr>
          <tr>
            <td><strong>Paginação</strong></td>
            <td>
              Componente <code>&lt;Pagination /&gt;</code> para controlar a navegação de grandes
              volumes de dados nas Data Tables.
            </td>
          </tr>
          <tr>
            <td><strong>Cards (Containers)</strong></td>
            <td>
              Componente <code>&lt;CardContainer /&gt;</code> que englobará informações do painel,
              garantindo padronização de bordas, fundos suaves e cabeçalhos em toda a aplicação.
            </td>
          </tr>
          <tr>
            <td><strong>Alerts (Caixas de Mensagem)</strong></td>
            <td>
              Componente <code>&lt;AlertBox /&gt;</code> para exibir notificações de erro, sucesso
              ou informação dinamicamente na interface.
            </td>
          </tr>
          <tr>
            <td><strong>Modais</strong></td>
            <td>
              Componente <code>&lt;ModalDialog /&gt;</code> para sobreposição na tela quando uma
              confirmação ou edição de dados rápida for necessária (ex: aprovar base).
            </td>
          </tr>
          <tr>
            <td><strong>Map Container (Leaflet)</strong></td>
            <td>
              Componente <code>&lt;SpatialMap /&gt;</code> isolado para carregar e gerenciar
              instâncias do mapa interativo, lidando com camadas, polígonos e controles
              geoespaciais.
            </td>
          </tr>
          <tr>
            <td><strong>Charts (Gráficos)</strong></td>
            <td>
              Componentes <code>&lt;LineChart /&gt;</code>, <code>&lt;BarChart /&gt;</code> usando
              Chart.js para visualização de estatísticas do DataHub.
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.styleguide {
  padding: 3rem;
  max-width: 1200px;
  margin: 0 auto;
}
.header-section {
  margin-bottom: 4rem;
  padding-bottom: 2rem;
  border-bottom: 2px solid var(--color-border);
}
.header-section h1 {
  font-size: 2.5rem;
  color: var(--color-heading);
  margin-bottom: 1rem;
}
section {
  margin-bottom: 5rem;
}
section h2 {
  font-size: 1.8rem;
  color: var(--color-heading);
  margin-bottom: 0.5rem;
}
.section-desc {
  opacity: 0.7;
  margin-bottom: 2rem;
}
.color-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 1.5rem;
}
.color-box {
  height: 120px;
  border-radius: 8px;
  display: flex;
  align-items: flex-end;
  padding: 1rem;
  font-weight: 500;
  font-size: 0.85rem;
  color: white;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}
.color-box span {
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
}
.color-box[style*='--vis-c-light'] span {
  text-shadow: none;
  font-weight: 600;
}
code {
  background: var(--color-background-mute);
  padding: 0.3rem 0.5rem;
  border-radius: 4px;
  font-family: monospace;
  font-size: 0.9em;
}
</style>
