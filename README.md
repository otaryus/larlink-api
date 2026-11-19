# LarLink API

API REST em Spring Boot com PostgreSQL para os fluxos do frontend LarLink. O frontend permanece com os seus ecrãs simples; esta API centraliza autenticação, autorização, perfis, pesquisa de compatibilidade e pedidos de agenciamento.

## Arranque local

1. Inicie a base de dados em `api/` com `docker compose up -d`.
2. Inicie a API em modo de desenvolvimento com `SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run` (Windows: `set SPRING_PROFILES_ACTIVE=dev && mvnw.cmd spring-boot:run`).
3. A API fica em `http://localhost:8080`; OpenAPI UI em `/swagger-ui/index.html`.

PostgreSQL local (container): base `larlink`, utilizador `larlink`, palavra-passe `larlink2026`, publicada na porta `5433` do computador para não conflituar com uma instalação PostgreSQL local na porta `5432`. A aplicação usa `prod` por padrão; defina `SPRING_PROFILES_ACTIVE=dev` apenas para desenvolvimento local, onde são habilitados dados de demonstração. Pode alterar `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MS` e `CORS_ORIGIN` via ambiente. Defina `JWT_SECRET` com pelo menos 32 bytes antes de qualquer implantação. Para criar o primeiro agente, configure `AGENT_EMAIL` e `AGENT_PASSWORD` antes de iniciar a API; opcionalmente defina `AGENT_NAME`. Agentes autenticados também podem criar outros agentes por `POST /api/agent/users`. O bootstrap só cria a conta se o email ainda não existir.

Para redigir minutas com IA, configure `OPENAI_API_KEY`; `OPENAI_MODEL` pode substituir o modelo configurado por padrão. Sem uma chave, a API cria uma minuta estruturada local e marca `aiGenerated=false`. A minuta é sempre apresentada para revisão da agência e das partes antes da assinatura.

O ranking inteligente do matching usa 50% de pontuação determinística e 50% da ordem produzida pelo DeepSeek. O distrito não precisa coincidir exactamente: a busca calcula a distância geográfica entre distritos, aceita candidatas até 25 km e atribui maior percentagem de proximidade às mais próximas. A resposta inclui `distanceKm`, `proximityScore` e `maximumDistanceKm`. Em desenvolvimento, coloque `DEEPSEEK_API_KEY` em `api/.env`; o Spring importa esse ficheiro opcionalmente. `DEEPSEEK_MODEL` pode substituir `deepseek-chat` e `DEEPSEEK_BASE_URL` pode substituir `https://api.deepseek.com`. A API envia apenas identificadores técnicos, localização, competências, disponibilidade, salário e experiência — nunca nomes, emails, telefones ou fotografias. Se o serviço estiver indisponível ou existir apenas uma candidata elegível, o ranking usa 100% da pontuação determinística.

## Autenticação e perfis

`POST /api/auth/register` cria contas CLIENT, HOUSEKEEPER ou CANDIDATE; `AGENT` é reservado à administração. O registo devolve `token`, `id`, `name`, `email` e `role`. `POST /api/auth/login` devolve o mesmo formato. Envie o token nas rotas privadas com `Authorization: Bearer <token>`. Palavras-passe são armazenadas com BCrypt. Autorização é aplicada no servidor com Spring Security e `@PreAuthorize`.

Perfis suportados: `AGENT` (gestão da agência e dashboard), `CLIENT` (pesquisa e pedido), `CANDIDATE` (perfil em busca de colocação), `HOUSEKEEPER` (perfil de empregada). A criação pública de conta de agente é bloqueada.

## Rotas principais

| Rota | Perfil | Uso |
|---|---|---|
| `POST /api/auth/register`, `POST /api/auth/login` | Público | Criar conta e iniciar sessão |
| `POST /api/clients/profile` | CLIENT | Completar telefone e endereço |
| `GET /api/clients/me` | CLIENT | Consultar o perfil próprio |
| `POST /api/housekeepers/profile` | HOUSEKEEPER, CANDIDATE | Registar disponibilidade, competências e salário |
| `GET /api/housekeepers`, `PATCH /api/housekeepers/{id}/decision` | AGENT | Rever perfis e decidir aprovação |
| `GET /api/housekeepers/me` | HOUSEKEEPER, CANDIDATE | Consultar o perfil próprio |
| `GET/POST /api/matching` | CLIENT | Encontrar perfis aprovados, filtrados por cidade, competências, dias, orçamento e texto |
| `POST /api/requests`, `GET /api/requests` | CLIENT | Criar pedido e consultar os próprios pedidos |
| `GET /api/requests` | AGENT | Consultar pedidos da agência |
| `GET /api/requests/inbox`, `PATCH /api/requests/{id}/worker-decision` | HOUSEKEEPER, CANDIDATE | Consultar convite validado pela agência e aceitar/recusar; a aceitação gera uma minuta de contrato |
| `PATCH /api/requests/{id}/decision` | AGENT | Aprovar ou rejeitar pedido |
| `GET /api/contracts`, `PATCH /api/contracts/{id}/sign` | CLIENT, HOUSEKEEPER, CANDIDATE, AGENT | Consultar minutas e recolher assinaturas das partes |
| `POST /api/incidents` | CLIENT, HOUSEKEEPER, CANDIDATE | Reportar ocorrência vinculada a contrato activo; o registo fica visível apenas à agência |
| `GET /api/agent/incidents`, `PATCH /api/agent/incidents/{id}/close` | AGENT | Consultar e tratar ocorrências confidenciais |
| `POST /api/agent/users` | AGENT | Criar outro acesso de agente |
| `PATCH /api/housekeepers/{id}/decision` | AGENT | Aprovar ou rejeitar perfil |
| `GET /api/agent/dashboard` | AGENT | Indicadores da agência |

O matching combina correspondência de competências, dias, cidade, orçamento e experiência; apenas perfis aprovados aparecem. O pedido do cliente regista escopo textual de tarefas, salário mensal, duração, horas diárias, dias e início. A agência encaminha o pedido à trabalhadora; ao aceitar, é gerada uma minuta com todo o escopo, disponibilizada para assinatura de ambas as partes e revisão da agência. O fluxo inclui ocorrências confidenciais, visíveis apenas à agência. O esquema JPA é criado/actualizado no arranque (`ddl-auto=update`) durante o desenvolvimento.

## Diagnóstico PostgreSQL

O serviço Compose cria automaticamente a base `larlink` na primeira inicialização e a aplicação cria/actualiza as tabelas JPA no arranque. Se aparecer `password authentication failed for user "larlink"`, confirme que a aplicação liga à porta `5433` do container, e não a uma instalação PostgreSQL local. `POSTGRES_PASSWORD` só configura o utilizador quando o volume Docker é criado pela primeira vez. Para manter dados existentes, entre no contentor com `docker exec -it larlink-postgres psql -U larlink -d larlink` e execute `ALTER USER larlink WITH PASSWORD 'larlink2026';`. Apagar o volume reinicializa a base e elimina todos os dados nele guardados.

## Arquitectura e regras de evolução

A API segue uma separação por camadas compatível com Clean Architecture:

- `domain`: entidades e máquinas de estado do negócio.
- `service`: políticas e casos de uso reutilizáveis, incluindo negociação salarial, cálculo financeiro, documentos, fotografias e auditoria.
- `repo`: portas de persistência implementadas por Spring Data JPA.
- `web`: adaptadores HTTP, DTOs, autorização e mapeamento de respostas.
- `security`: autenticação JWT, RBAC e bloqueio do primeiro acesso.

Novas regras de negócio devem ser implementadas e testadas em `domain` ou `service`. Controllers não devem duplicar cálculos ou transições já existentes.

## Estratégia de testes

O desenvolvimento usa TDD e regressão por funcionalidade:

```bash
./mvnw test
```

A suite cobre autenticação e RBAC, primeiro acesso, fotografias, catálogo de tarefas, negociação salarial, contratos, faltas, serviços adicionais, pagamentos idempotentes, provider não configurado, consolidação financeira, notificações, auditoria e relatórios.

Antes de concluir uma alteração:

1. Criar um teste que reproduza a regra ou falha.
2. Confirmar a falha.
3. Implementar a menor correção possível.
4. Executar testes focados e depois a suite completa.

## Pagamentos

`PaymentProvider` é a porta para uma integração autorizada. O adaptador padrão é `UnconfiguredPaymentProvider`, que não cria referências nem confirma pagamentos. Uma integração real deve validar callbacks assinados e usar identificadores únicos do prestador; nunca simule confirmação bancária.

## Segurança operacional

- Fotografias e anexos são validados por tamanho, MIME declarado e assinatura binária.
- Documentos e imagens são servidos apenas a utilizadores autorizados.
- Palavras-passe temporárias são armazenadas somente como hash e bloqueiam os restantes endpoints até serem alteradas.
- Operações críticas geram eventos de auditoria.
- Defina `JWT_SECRET` com no mínimo 32 bytes fora do ambiente local.
