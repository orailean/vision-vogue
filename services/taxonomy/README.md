# Vision Vogue Taxonomy

A Spring Boot application for AI-powered fashion image analysis and semantic search with an integrated Angular search widget.

## 🎯 Features

- 📸 **Image Analysis**: Automatic processing of fashion images using external AI API
- 🔍 **Semantic Search**: Natural language search powered by sentence transformers
- 🎨 **Interactive Widget**: Angular 20 + PrimeNG carousel for visual search results
- 👔 **Multi-Partner**: Support for multiple partner organizations with automatic directory structure creation
- 🗄️ **PostgreSQL Storage**: Structured data with JSONB for flexible schema
- 📊 **REST API**: Full REST endpoints with Swagger UI documentation
- 🐳 **Docker Support**: Easy PostgreSQL setup with Docker Compose

---

## 📋 Table of Contents

- [Requirements](#requirements)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Search Widget](#search-widget)
- [API Endpoints](#api-endpoints)
- [File Processing](#file-processing)
- [Database Schema](#database-schema)
- [Development](#development)
- [Deployment](#deployment)

---

## 🔧 Requirements

### Backend
- Java 17+
- Maven 3.9+
- PostgreSQL 13+

### External Services
- **Analyzer API**: `http://127.0.0.1:8000/analyze` (Python FastAPI for image classification)
- **Embedding API**: `http://127.0.0.1:8000/embed` (Python FastAPI for semantic embeddings)

### Frontend (Widget)
- Node.js v22.12.0+ (auto-installed via Maven plugin)
- NPM v10.9.2+

---

## 🚀 Quick Start

### 1. Start PostgreSQL

**Using Docker Compose (Recommended)**:
```bash
docker compose up -d db
```

**Or manually with Docker**:
```bash
docker run --name vision_vogue_db \
  -e POSTGRES_DB=vision_vogue \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 -d postgres:15
```

### 2. Build the Application

```bash
mvn clean package
```

This automatically:
- Compiles Java code
- Builds the Angular widget
- Bundles everything into a JAR

### 3. Run the Application

```bash
mvn spring-boot:run
```

### 4. Access the Services

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Search Widget**: http://localhost:8080/widget/{partnerId}
- **OpenAPI Docs**: http://localhost:8080/v3/api-docs

**Example Widget URL**:
```
http://localhost:8080/widget/8d3a83ff-5a9f-4d57-8671-9397c1b02a25
```

---

## ⚙️ Configuration

Edit `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/vision_vogue
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    baseline-on-migrate: true

server:
  port: 8080

app:
  analyze-url: http://127.0.0.1:8000/analyze
  income-dir: data/income
  processed-dir: data/processed
  failed-dir: data/failed
  top-k-category: 3
  top-per-attribute: 1
  n-colors: 5

embedding:
  api:
    url: http://127.0.0.1:8000/embed

semantic:
  search:
    min-similarity-threshold: 0.35  # Filter results with similarity < 35%
```

### Configuration Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `app.analyze-url` | External image analyzer API endpoint | `http://127.0.0.1:8000/analyze` |
| `app.income-dir` | Directory for incoming images | `data/income` |
| `app.processed-dir` | Directory for processed images | `data/processed` |
| `app.failed-dir` | Directory for failed images | `data/failed` |
| `embedding.api.url` | External embedding API endpoint | `http://127.0.0.1:8000/embed` |
| `semantic.search.min-similarity-threshold` | Minimum cosine similarity for search results | `0.35` |

---

## 🎨 Search Widget

### Overview

The Angular-based search widget provides an intuitive interface for semantic product search.

**Technology Stack**:
- Angular 20
- PrimeNG 20 (UI components)
- RxJS (reactive programming)

### Features

✅ Natural language search (e.g., "red hats", "blue sneakers")  
✅ PrimeNG Carousel for visual results  
✅ Responsive design (mobile, tablet, desktop)  
✅ Real-time search with Enter key  
✅ Score visualization (similarity, confidence, combined)  
✅ Automatic image loading with fallback  
✅ Configurable similarity threshold filtering

### Access the Widget

```
http://localhost:8080/widget/{partnerId}
```

### Search Examples

Try these queries:
- `"red hats"`
- `"blue sneakers"`
- `"kids t-shirts"`
- `"women's accessories"`
- `"sport shoes"`

### Widget Development

**Watch mode** (for frontend development):
```bash
cd src/main/webapp
npm install
npm run watch
```

**Build manually**:
```bash
cd src/main/webapp
npm run build
```

Output location: `src/main/resources/static/widget/browser/`

### Customization

**Theme Configuration**:
- Edit `src/main/webapp/src/app/app.config.ts`

**Custom Styles**:
- Edit `src/main/webapp/src/app/components/search-widget/search-widget.scss`

---

## 🌐 API Endpoints

### Image Processing

#### Process Incoming Images
```http
POST /api/process/income
```

Scans `app.income-dir` for new images and processes them.

**Response**: List of `ProcessingResult` objects

---

### Analysis Results

#### Get All Results
```http
GET /api/results
```

Returns all analysis records.

#### Get Single Result
```http
GET /api/results/{id}
```

Returns a specific analysis record by UUID.

---

### Semantic Search

#### Search Products
```http
GET /api/search/semantic?partnerId={uuid}&q={prompt}&topK={n}&simWeight={weight}
```

**Parameters**:
- `partnerId` (required): Partner UUID
- `q` (required): Search query (natural language)
- `topK` (optional): Maximum results, default `10`
- `simWeight` (optional): Similarity weight `0.0-1.0`, default `0.8`

**Example**:
```bash
curl -X 'GET' \
  'http://localhost:8080/api/search/semantic?partnerId=8d3a83ff-5a9f-4d57-8671-9397c1b02a25&q=red%20hats&topK=5&simWeight=0.8' \
  -H 'accept: application/json'
```

**Response**:
```json
[
  {
    "recordId": "uuid",
    "filename": "hat_1.jpg",
    "topCategoryLabel": "HAT",
    "topCategoryConfidence": 0.95,
    "similarity": 0.87,
    "combinedScore": 0.886,
    "text": "category: HAT\nfilename: hat_1.jpg\ncolor: red..."
  }
]
```

**Scoring Formula**:
```
combinedScore = simWeight × similarity + (1 - simWeight) × topCategoryConfidence
```

---

### Partners

#### Create Partner
```http
POST /api/partners
Content-Type: application/json

{
  "name": "Acme Fashion Corp"
}
```

**Response**: Partner object with generated UUID

#### Get All Partners
```http
GET /api/partners
```

**Response**: Array of all partner objects
```json
[
  {
    "id": "8d3a83ff-5a9f-4d57-8671-9397c1b02a25",
    "name": "Acme Fashion Corp"
  },
  {
    "id": "b4d3e783-19fd-4c49-bbaf-099d2b957146",
    "name": "Fashion Retailer Co"
  }
]
```

#### Get Partner by ID
```http
GET /api/partners/{id}
```

**Response**: Single partner object or 404 if not found

---

### Widget & Images

#### Serve Widget
```http
GET /widget/{partnerId}
```

Serves the Angular search widget application.

#### Get Product Image
```http
GET /api/images/{partnerId}/{filename}
```

Serves processed product images.

---

## 📁 File Processing

### Directory Structure

```
data/
├── income/                          # Drop images here
│   └── {partnerId}/                # Partner-specific folder
│       ├── image1.jpg
│       └── image2.png
├── processed/                       # Successfully processed
│   └── {partnerId}/
│       ├── image1.jpg
│       └── image2.png
└── failed/                         # Failed processing
    └── {partnerId}/
        └── bad_image.jpg
```

### Processing Flow

1. **Drop Images**: Place images in `data/income/{partnerId}/`
2. **Auto-Detection**: App scans for new files automatically
3. **Analysis**: Each file sent to analyzer API
4. **Storage**: Results saved to PostgreSQL
5. **Move Files**: 
   - Success → `data/processed/{partnerId}/`
   - Failure → `data/failed/{partnerId}/`

### Supported Image Formats

- **JPEG**: `.jpg`, `.jpeg`, `.jpe`, `.jfif`
- **PNG**: `.png`
- **WebP**: `.webp`
- **AVIF**: `.avif`
- **HEIC/HEIF**: `.heic`, `.heif`
- **TIFF**: `.tif`, `.tiff`
- **GIF**: `.gif`
- **BMP**: `.bmp`
- **SVG**: `.svg`
- **ICO**: `.ico`
- **JPEG2000**: `.jp2`, `.j2k`

### Partner Setup

1. Create a partner via API:
```bash
curl -X POST http://localhost:8080/api/partners \
  -H 'Content-Type: application/json' \
  -d '{"name": "Acme Corp"}'
```

2. Copy the returned `id` (UUID)

3. **Partner directories are automatically created** in:
   - `data/income/{partnerId}/` - Drop incoming images here
   - `data/processed/{partnerId}/` - Successfully processed images
   - `data/failed/{partnerId}/` - Failed processing images

4. Drop images into the `data/income/{partnerId}/` folder

---

## 🗄️ Database Schema

### Tables

#### `analysis_records`
| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key |
| `filename` | VARCHAR | Original filename |
| `status` | VARCHAR | SUCCESS or FAILED |
| `created_at` | TIMESTAMP | Processing timestamp |
| `top_category_label` | VARCHAR | Top classification category |
| `top_category_confidence` | DOUBLE | Confidence score (0-1) |
| `category_json` | JSONB | All category predictions |
| `attributes_json` | JSONB | Detected attributes |
| `colors_json` | JSONB | Extracted colors |
| `raw_json` | JSONB | Full API response |
| `error_message` | TEXT | Error details (if failed) |
| `partner_id` | UUID | Foreign key to partners |

#### `partners`
| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key |
| `name` | VARCHAR | Partner name |

### Migrations

Database migrations managed by **Flyway**:
- Location: `src/main/resources/db/migration/`
- Naming: `V{version}__{description}.sql`
- Auto-runs on application startup

---

## 💻 Development

### Project Structure

```
vision-vogue-taxonomy/
├── src/main/
│   ├── java/com/visionvogue/analyzer/
│   │   ├── model/              # JPA entities
│   │   ├── repo/               # Spring Data repositories
│   │   ├── service/            # Business logic
│   │   ├── web/                # REST controllers
│   │   └── config/             # Spring configuration
│   ├── resources/
│   │   ├── application.yml     # Configuration
│   │   ├── logback-spring.xml  # Logging config
│   │   ├── db/migration/       # Flyway migrations
│   │   └── static/widget/      # Angular build output
│   └── webapp/                 # Angular application
│       ├── src/
│       │   ├── app/
│       │   │   ├── components/ # Angular components
│       │   │   └── services/   # HTTP services
│       │   └── styles.scss
│       ├── angular.json
│       └── package.json
├── data/                       # Image directories
├── logs/                       # Application logs
├── docker-compose.yml
└── pom.xml
```

### Building

**Full build** (includes Angular):
```bash
mvn clean package
```

**Skip frontend** (faster for backend-only changes):
```bash
mvn clean package -Dskip.npm
```

**Java only** (no tests):
```bash
mvn clean compile -DskipTests
```

### Running

**Spring Boot**:
```bash
mvn spring-boot:run
```

**With profile**:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

**From JAR**:
```bash
java -jar target/vision-vogue-analyzer-0.0.1-SNAPSHOT.jar
```

### Logging

- **Console**: Errors only
- **File**: `logs/app-{date}.log` (DEBUG and above)
- **Rotation**: Daily
- **Config**: `src/main/resources/logback-spring.xml`

### Troubleshooting

#### Widget not loading
- Verify build: `ls -la src/main/resources/static/widget/browser/`
- Check logs for static resource errors
- Ensure base href is `/widget/`

#### No search results
- Check embedding API is running: `curl http://127.0.0.1:8000/health`
- Verify similarity threshold in `application.yml`
- Check logs for API connection errors

#### Images not displaying
- Verify images in `data/processed/{partnerId}/`
- Check file permissions
- Browser console for 404 errors

#### Database connection failed
- Ensure PostgreSQL is running: `docker ps`
- Check credentials in `application.yml`
- Verify database exists: `psql -U postgres -l`

---

## 🚢 Deployment

### Production Checklist

- [ ] Set `spring.jpa.hibernate.ddl-auto: validate`
- [ ] Enable Flyway: `spring.flyway.enabled: true`
- [ ] Use environment variables for secrets
- [ ] Configure external PostgreSQL
- [ ] Set up reverse proxy (nginx/Apache)
- [ ] Enable HTTPS
- [ ] Configure CORS if needed
- [ ] Set up monitoring/logging
- [ ] Configure file backup strategy

### Environment Variables

Override configuration via environment:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://prod-db:5432/vision_vogue
export SPRING_DATASOURCE_USERNAME=vv_user
export SPRING_DATASOURCE_PASSWORD=secure_password
export APP_ANALYZE_URL=https://analyzer.prod.example.com/analyze
export EMBEDDING_API_URL=https://embedding.prod.example.com/embed
export SEMANTIC_SEARCH_MIN_SIMILARITY_THRESHOLD=0.40

java -jar vision-vogue-analyzer.jar
```

### Docker Deployment

**Build image**:
```bash
docker build -t vision-vogue-analyzer .
```

**Run with Docker Compose**:
```bash
docker compose up -d
```

---

## 📊 Semantic Search Architecture

```
User Browser
    ↓
Angular Widget (http://localhost:8080/widget/{partnerId})
    ↓
SearchService → GET /api/search/semantic
    ↓
SemanticSearchService
    ├─→ POST http://127.0.0.1:8000/embed (query)
    ├─→ POST http://127.0.0.1:8000/embed (each product)
    ↓
Calculate cosine similarity
    ↓
Filter by min-similarity-threshold (0.35)
    ↓
Rank by combined score
    ↓
Return top K results
    ↓
Display in PrimeNG Carousel
```

### How It Works

1. **Query Embedding**: User query converted to vector via embedding API
2. **Product Embedding**: Each product's text (category, attributes, colors) embedded
3. **Similarity Calculation**: Cosine similarity between query and products
4. **Filtering**: Results below threshold (default 0.35) excluded
5. **Ranking**: Combined score = `simWeight × similarity + (1 - simWeight) × confidence`
6. **Display**: Top K results shown in carousel

---

## 📝 License

[Your License Here]

## 🤝 Contributing

[Your Contributing Guidelines Here]

## 📧 Contact

[Your Contact Information Here]

---

**Built with ❤️ using Spring Boot, Angular, and AI**

