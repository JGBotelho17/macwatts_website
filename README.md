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
