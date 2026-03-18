# Projeto Pequeno de Geocoding + Mapa

Este projeto recebe uma morada, faz geocoding com o Nominatim (OpenStreetMap) e mostra o local no mapa com Leaflet.

## Requisitos

- Java 17
- Maven instalado (`mvn`)

## Como executar

```bash
mvn spring-boot:run
```

Depois abre:

`http://localhost:8080`

## Como usar

1. Escreve uma morada no campo de pesquisa.
2. Clica em **Localizar**.
3. O mapa centra no ponto encontrado e mostra um marcador com a morada resolvida.

## Endpoint usado

- `GET /api/geocode?address=<morada>`

Exemplo:

`/api/geocode?address=Avenida%20da%20Liberdade%2C%20Lisboa`

## Deploy Web (GitHub Pages)

O projeto foi preparado para publicar o frontend estático (`src/main/resources/static`) no GitHub Pages com GitHub Actions.

### Modo frontend-only (sem backend)

Se quiseres apenas frontend por enquanto:

1. Em `src/main/resources/static/config.js`, deixa:
   - `API_BASE_URL: ""`
   - `QUOTE_EMAIL_TO: "teu-email@dominio.com"`
2. O geocoding passa a usar Nominatim direto no browser.
3. O envio de orçamento é feito direto para email via FormSubmit (sem Spring).
4. No primeiro envio, o FormSubmit pede confirmação no email de destino para ativar.

### 1) Ativar Pages no repositório

1. GitHub -> `Settings` -> `Pages`
2. Em `Source`, escolhe `GitHub Actions`

### 2) Fazer push para `main`

Existe um workflow em `.github/workflows/deploy-pages.yml`.
Depois do push, o site é publicado no endereço do Pages do repositório.

### 3) Configurar o endpoint do backend no frontend

Edita `src/main/resources/static/config.js`:

```js
window.APP_CONFIG = window.APP_CONFIG || {
  API_BASE_URL: "https://SEU-BACKEND-ONLINE"
};
```

Sem `API_BASE_URL`, o frontend usa requests relativas (`/api/...`), ideal para ambiente local.

## Deploy do Backend (Spring Boot)

GitHub Pages não executa Java/Spring, por isso o backend deve ser publicado noutra plataforma (Render, Railway, Fly.io, etc).

### CORS para o domínio do Pages

Define a variável de ambiente no serviço do backend:

`APP_CORS_ALLOWED_ORIGINS=https://<teu-utilizador>.github.io`

Se necessário, podes passar múltiplos domínios separados por vírgula:

`APP_CORS_ALLOWED_ORIGINS=https://<teu-utilizador>.github.io,https://www.teu-dominio.com`

## Integracao com Odoo (Contacto + Lead CRM)

Quando o frontend envia para `POST /api/quote/email`, o backend pode:

1. enviar email (SMTP), e
2. criar/atualizar um contacto em `res.partner` e criar uma lead em `crm.lead`.

Ativa no ambiente do backend:

- `APP_ODOO_ENABLED=true`
- `APP_ODOO_BASE_URL=https://teu-odoo.com`
- `APP_ODOO_DB=nome_da_base`
- `APP_ODOO_USERNAME=utilizador_odoo`
- `APP_ODOO_PASSWORD=password_ou_api_key`
- `APP_ODOO_CRM_RECORD_TYPE=opportunity` (ou `lead`)
- `APP_ODOO_CRM_TEAM_ID=0` (opcional, para forçar canal/equipa no Kanban)
- `APP_ODOO_CRM_USER_ID=0` (opcional, para forçar vendedor)

Notas:

- O frontend precisa de apontar para o backend (`API_BASE_URL` em `config.js`) para esta integração acontecer.
- Se `APP_ODOO_ENABLED=false`, o backend ignora Odoo.

## Base de dados da API (simples)

O backend usa uma base de dados H2 local em ficheiro para guardar os submits:

- caminho default: `./data/oficina.mv.db`
- tabela principal: `quote_submission`
- estado por submit: `PROCESSING`, `SUCCESS`, `FAILED`

Configuração default (já ativa):

- `spring.datasource.url=jdbc:h2:file:./data/oficina...`
- `spring.jpa.hibernate.ddl-auto=update`

Opcional:

- ativar consola H2: `H2_CONSOLE_ENABLED=true`
- abrir em: `/h2-console`
