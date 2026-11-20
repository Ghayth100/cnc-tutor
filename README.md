# Tuteur – Programmation CNC

Chatbot web (Java 21 / Spring Boot 3.5) qui explique **uniquement** le cours « Programmation CNC ».

- Le cours est fixe : `src/main/resources/cours/PROGRAMMATION_CNC.pdf` (pas d'upload).
- Au démarrage, le texte est extrait page par page avec Apache PDFBox et gardé en mémoire avec des marqueurs `[Page N]`.
- À chaque question, l'application envoie à une API LLM compatible OpenAI (`/v1/chat/completions`) : le prompt système, le cours complet, les 10 derniers messages et la question. La réponse s'affiche en streaming.
- Aucun modèle à entraîner ni à héberger : c'est un simple appel HTTP.

## 1. Obtenir une clé API gratuite (Groq)

1. Créez un compte sur <https://console.groq.com>.
2. Menu **API Keys** → **Create API Key**, puis copiez la clé (`gsk_…`). Elle ne s'affiche qu'une fois.

## 2. Définir la variable d'environnement `LLM_API_KEY`

La clé n'est **jamais** écrite dans le code ni dans `application.properties` : elle est lue depuis la variable d'environnement `LLM_API_KEY`.

**Option la plus simple : fichier `.env`.** Copiez `.env.example` en `.env` à la racine du projet et remplacez la valeur par votre clé. L'application le lit au démarrage, et `.env` est ignoré par git. Une variable d'environnement définie dans le shell est prioritaire sur ce fichier.

**Windows PowerShell** (session courante) :
```powershell
$env:LLM_API_KEY = "gsk_votre_cle"
```
Pour la rendre permanente (nouvelle fenêtre de terminal ensuite) :
```powershell
[Environment]::SetEnvironmentVariable("LLM_API_KEY", "gsk_votre_cle", "User")
```

**Linux / macOS** (bash, zsh) :
```bash
export LLM_API_KEY="gsk_votre_cle"
# permanent : ajoutez la ligne ci-dessus à ~/.bashrc ou ~/.zshrc
```

## 3. Lancer l'application

Prérequis : JDK 21 ou plus récent. Maven est facultatif, le wrapper fourni le télécharge.

```bash
mvn spring-boot:run          # si Maven est installé
./mvnw spring-boot:run       # Linux / macOS, sans Maven
.\mvnw.cmd spring-boot:run   # Windows, sans Maven
```

Ouvrez ensuite <http://localhost:8080>.

Sans clé, l'application démarre quand même : le chat affiche alors un message expliquant comment définir `LLM_API_KEY`.

## Hébergement public (Render, gratuit)

Le dépôt contient un `Dockerfile` et un `render.yaml`. Render compile l'image depuis GitHub et fournit une adresse HTTPS publique du type `https://cnc-tutor-xxxx.onrender.com`.

1. Créez un compte sur <https://render.com> avec **« Sign in with GitHub »**.
2. **New +** → **Blueprint** → autorisez l'accès au dépôt `cnc-tutor` → sélectionnez-le.
3. Render lit `render.yaml` et demande la valeur de **`LLM_API_KEY`** : collez votre clé Groq. Elle reste stockée chez Render, jamais dans le dépôt.
4. **Apply**. Le premier build prend 5 à 10 minutes, puis l'URL publique s'affiche en haut de la page du service.

Chaque `git push` sur `main` redéploie automatiquement.

Sans Blueprint, la création manuelle fonctionne aussi : **New +** → **Web Service** → le dépôt → Runtime **Docker**, Instance **Free**, variable d'environnement `LLM_API_KEY`.

**Limites de l'offre gratuite**
- Le service **s'endort après 15 minutes sans visite**. La visite suivante attend environ 1 minute le temps du réveil.
- Le quota Groq gratuit (environ 8 000 tokens par minute) est **partagé par tous les visiteurs** : environ une question par minute au total. Chaque visiteur est aussi limité à `chat.rate-limit-per-minute=3` questions par minute.
- Le PDF du cours devient accessible publiquement (pages en image). Vérifiez que vous avez le droit de le diffuser.

**Garde-fous pour un site public** (`application.properties`) : `chat.rate-limit-per-minute` (questions par IP et par minute), `chat.session-ttl` et `chat.max-sessions` (historiques en mémoire bornés).

**Tester l'image localement :**
```bash
docker build -t cnc-tutor .
docker run -p 8080:8080 -e LLM_API_KEY=gsk_... cnc-tutor
```

## Tests

```bash
mvn test        # ou .\mvnw.cmd test
```
- `CourseLoaderTest` : le PDF a des pages, et chaque page a son marqueur `[Page N]`.
- `TutorServiceTest` : assemblage des messages, historique (10 messages max), isolement des sessions, erreurs (`LlmClient` mocké avec Mockito).

## API

| Méthode | URL | Corps | Réponse |
|---|---|---|---|
| POST | `/api/chat` | `{ "sessionId": "...", "message": "..." }` | flux SSE : évènements `token` `{"t":"..."}`, puis `done` ou `error` `{"code","message"}` |
| POST | `/api/reset` | `{ "sessionId": "..." }` | `{ "status": "ok" }` |
| GET | `/api/cours/page/{n}` | – | image PNG de la page n (120 dpi, en cache) |
| GET | `/api/cours/info` | – | `{ "pages": 105 }` |

## Configuration (`application.properties`)

| Propriété | Défaut |
|---|---|
| `llm.base-url` | `https://api.groq.com/openai/v1` |
| `llm.model` | `openai/gpt-oss-120b` |
| `llm.reasoning-effort` | `low` (modèles gpt-oss ; laisser vide pour les autres) |
| `cours.max-context-chars` | `9000` (au-delà : plan + pages pertinentes) |
| `chat.history-max-chars` | `3000` |
| `llm.api-key` | `${LLM_API_KEY:}` |
| `llm.temperature` | `0.3` |
| `llm.max-tokens` | `2048` |
| `server.port` | `${PORT:8080}` (variable `PORT` pour changer) |
| `llm.read-timeout` | `120s` |
| `chat.history-size` | `10` |

Toute API compatible OpenAI convient (OpenAI, Mistral, OpenRouter, Ollama local…). Il suffit de changer `llm.base-url` et `llm.model`.

## Quotas du compte gratuit Groq : envoi du cours complet ou par extraits

`llama-3.3-70b-versatile` a été retiré par Groq. Le modèle par défaut est donc `openai/gpt-oss-120b`. Pour voir la liste à jour des modèles : `GET https://api.groq.com/openai/v1/models`.

Le cours complet représente environ 9 000 tokens. Or, sur l'offre gratuite, **chaque modèle est limité à 8 000 tokens par minute**. Le cours entier ne passe donc pas en une seule requête. L'application choisit automatiquement :

- **Cours complet** si sa taille est inférieure ou égale à `cours.max-context-chars`. C'est le fonctionnement prévu au départ, à utiliser avec une offre payante : mettez `cours.max-context-chars=100000`.
- **Plan + pages pertinentes** sinon. C'est le réglage par défaut (`9000`). `CourseRetriever` classe les pages selon les mots de la question et de la question précédente, avec un fort poids sur les codes G/M. Il envoie le plan du cours (titre de chaque page) et le texte complet des meilleures pages, dans la limite du budget.

L'historique envoyé est aussi plafonné (`chat.history-max-chars=3000`). Même avec ces réglages, comptez environ **une question par minute** sur l'offre gratuite. Au-delà, le chat affiche un message clair (erreur 429 ou 413) : il suffit d'attendre une minute.

## Structure

```
src/main/java/com/cnc/tutor/
├── CncTutorApplication.java
├── config/LlmProperties.java          @ConfigurationProperties("llm")
├── controller/ChatController.java     /api/chat (SSE), /api/reset
├── controller/CourseController.java   /api/cours/page/{n}, /api/cours/info
├── dto/                               records Java (ChatRequest, ChatMessage, …)
├── exception/GlobalExceptionHandler   @ControllerAdvice : clé absente, 429, timeout…
├── prompt/TutorPrompt.java            prompt système
└── service/
    ├── CourseLoader.java              extraction PDFBox (@PostConstruct)
    ├── LlmClient.java                 RestClient + Jackson, streaming SSE
    ├── TutorService.java              prompt + cours + historique + question
    ├── SessionStore.java              ConcurrentHashMap, 10 derniers messages
    └── PageImageService.java          PDFRenderer 120 dpi + cache
src/main/resources/
├── application.properties
├── cours/PROGRAMMATION_CNC.pdf
└── static/ index.html, style.css, app.js
```
