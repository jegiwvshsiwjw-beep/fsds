# Local Agent Bridge (Android)

Projeto Android com **dois APKs**:

- **Servidor** (`:server`): mantém uma API HTTP na porta `8765`, acessível por outros aparelhos/PCs da mesma rede Wi-Fi.
- **Cliente** (`:client`): permite testar a conexão e enviar mensagens ao servidor.

O servidor funciona como uma ponte para qualquer serviço de inferência que exponha uma API compatível com OpenAI. Ele **não baixa nem executa modelos de IA dentro do Android**. Para usar um modelo local, configure a URL de um servidor como Ollama ou LM Studio que esteja acessível pela rede.

## Compilar automaticamente pelo GitHub Actions

O repositório já inclui `.github/workflows/android.yml`. O GitHub compila os dois APKs automaticamente quando você envia código para `main` ou `master`, e também permite iniciar a compilação manualmente.

1. Crie um repositório no GitHub e envie **todo o conteúdo** desta pasta (`LocalAgentBridge`) para ele. Não envie apenas os arquivos de dentro das pastas `server` e `client`.
2. No GitHub, abra a aba **Actions** e habilite os workflows, caso o GitHub peça.
3. Para compilar manualmente, entre em **Actions → Android APK Build → Run workflow**. Um push para `main` ou `master` também inicia o build.
4. Quando terminar, abra a execução concluída, vá até **Artifacts** e baixe `LocalAgentBridge-Android-APKs`. O ZIP contém os dois APKs debug.

O workflow instala Java 17, Android SDK 35 e Gradle 8.9 no runner do GitHub. Não precisa configurar Android Studio nem adicionar secrets. Os APKs são de depuração (debug), não são releases assinadas para publicação na Play Store.

## Requisitos de build

- Android Studio recente
- JDK 17
- Android SDK 35
- Gradle 8.9 (o script baixa automaticamente na primeira execução, se não houver Gradle no PATH)

Abra a pasta `LocalAgentBridge` no Android Studio e aguarde o Gradle sincronizar. Para compilar pelo terminal do Windows, use:

```bat
build-all.bat
```

O script `gradlew.bat` usa Gradle instalado ou baixa o Gradle 8.9 automaticamente. Também é possível abrir o projeto no Android Studio e usar **Build → Make Project**. Para compilar individualmente:

```bat
build-server.bat
build-client.bat
```

Os APKs debug ficam em:

- `server/build/outputs/apk/debug/server-debug.apk`
- `client/build/outputs/apk/debug/client-debug.apk`

## 1. Configurar o servidor

1. Instale e abra **Local Agent Bridge — Servidor** no celular que ficará como servidor.
2. Na configuração do modelo, informe a URL-base OpenAI-compatible, o nome do modelo e, se necessário, a chave.
   - Exemplo de Ollama em outro PC: `http://192.168.1.20:11434/v1`
   - Exemplo de LM Studio em outro PC: `http://192.168.1.20:1234/v1`
3. Salve a configuração e toque em **INICIAR SERVIDOR**.
4. Anote o endereço IP mostrado e copie o token de acesso.
5. Mantenha o celular e o cliente na mesma rede Wi-Fi.

A URL da API será `http://IP-DO-CELULAR:8765/v1`.

## 2. Usar o cliente

1. Instale **Local Agent Bridge — Cliente** no outro Android (ou no mesmo, para teste).
2. Informe o endereço do servidor, o token e, opcionalmente, o nome de um modelo.
3. Toque em **TESTAR CONEXÃO** e depois envie uma mensagem.

## 3. API HTTP

Todas as rotas abaixo, exceto `GET /health`, exigem o cabeçalho:

```http
Authorization: Bearer SEU_TOKEN
```

| Método | Rota | Função |
|---|---|---|
| GET | `/health` | Verifica se o servidor está respondendo; não exige token |
| GET | `/v1/models` | Lista o modelo configurado |
| POST | `/v1/chat/completions` | Encaminha mensagens para o modelo configurado |
| GET | `/v1/config` | Mostra a configuração não secreta |
| GET | `/v1/agent/tools` | Lista ferramentas disponibilizadas pelo bridge |
| POST | `/v1/agent/tools/call` | Executa uma ferramenta permitida |

### Exemplo PowerShell

```powershell
$server = "http://192.168.1.50:8765"
$token = "COLE_SEU_TOKEN"
$headers = @{ Authorization = "Bearer $token" }

Invoke-RestMethod "$server/health"

$body = @{
  messages = @(@{ role = "user"; content = "Olá!" })
  stream = $false
} | ConvertTo-Json -Depth 8

Invoke-RestMethod "$server/v1/chat/completions" -Method Post -Headers $headers -ContentType "application/json" -Body $body
```

### Exemplo cURL

```bash
curl http://192.168.1.50:8765/v1/chat/completions \
  -H "Authorization: Bearer SEU_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"Olá"}],"stream":false}'
```

### Ferramentas para agentes

Listar ferramentas:

```http
GET /v1/agent/tools
```

Executar informações do aparelho:

```json
{
  "name": "device_info",
  "arguments": {}
}
```

Enviar para `POST /v1/agent/tools/call`. A ferramenta `echo` também está disponível e recebe `arguments.value`.

## Segurança e limites desta versão

- O servidor escuta em `0.0.0.0:8765` para aceitar conexões da rede local.
- Um token aleatório de 256 bits protege as rotas privadas. O botão **GERAR NOVO TOKEN** invalida o anterior.
- O endpoint `/health` é público e não revela o token.
- A conexão é HTTP sem TLS. Use apenas em uma rede confiável; não abra/encaminhe a porta `8765` para a internet.
- O servidor encaminha chamadas para um endpoint compatível com OpenAI e não executa modelos localmente.
- `stream=true` não é suportado nesta versão; envie `stream=false`.
- As ferramentas do aparelho são deliberadamente limitadas a `device_info` e `echo`. Não há execução de shell, leitura geral de arquivos, acesso a mensagens, câmera ou microfone.
- O Android pode encerrar serviços por economia de bateria. O servidor usa serviço em primeiro plano com notificação; desative otimizações de bateria para o app caso o fabricante do aparelho interrompa a execução.

## Estrutura

```text
LocalAgentBridge/
  server/   # APK servidor + API Ktor
  client/   # APK cliente HTTP
  docs/     # notas e exemplos
```
