# 🚀 CampusPulse

### AI-Driven Curriculum-to-Industry Gap Analyzer

CampusPulse is an AI-powered academic analytics platform that analyzes an educational curriculum against current industry job-market requirements to identify **skill gaps**, measure **curriculum-industry alignment**, and generate **AI-driven recommendations** for curriculum improvement.

---

## 🎯 Problem Statement

Academic curricula may not always evolve at the same pace as industry requirements.

There can be a gap between:

- What students learn in their academic curriculum
- What skills companies currently demand
- Emerging technologies expected from graduates

CampusPulse aims to identify these gaps automatically using **Natural Language Processing, embeddings, semantic similarity, job-market data and Large Language Models (LLMs).**

---

## 💡 Proposed Solution

CampusPulse follows this workflow:

```text
Academic Syllabus
       ↓
Text Extraction
       ↓
AI-Based Syllabus Structuring
       ↓
Curriculum Skills
       ↓
                    ← Job Market Data
                           ↓
                    Required Skills
                           ↓
                    Skill Embeddings
                           ↓
                Semantic Skill Matching
                           ↓
                    Gap Detection
                           ↓
                     AI Analysis
                           ↓
              AI Recommendations
                           ↓
                  Visual Dashboard
```

---

## 🏗️ System Architecture

```text
┌──────────────────────────────────────┐
│           React Frontend             │
│                                      │
│ Dashboard | Charts | Reports         │
└──────────────────┬───────────────────┘
                   │
                  REST
                   │
┌──────────────────▼───────────────────┐
│        Spring Boot Backend           │
│                                      │
│ Authentication                       │
│ Syllabus Module                      │
│ Job Market Module                    │
│ Embedding Module                     │
│ Skill Matching                       │
│ Gap Detection                        │
│ AI Analysis                          │
│ Recommendation Engine                │
│ Report Generation                    │
└───────────────┬───────────┬──────────┘
                │           │
                ▼           ▼
       ┌─────────────┐  ┌─────────────┐
       │ PostgreSQL  │  │   LLM API   │
       │ + pgvector  │  │ Embeddings  │
       └─────────────┘  └─────────────┘
```

---

## 🛠️ Technology Stack

### Backend

- Java
- Spring Boot
- Spring Web
- Spring Data JPA
- Hibernate
- Spring Security
- JWT
- Spring WebClient
- Maven

### Database

- PostgreSQL
- pgvector

### AI

- Large Language Model API
- Embedding API
- Semantic Similarity
- AI-based Gap Analysis
- AI Recommendation Engine

### Frontend

- React
- JavaScript
- Axios
- Tailwind CSS
- Recharts

### Document Processing

- Apache PDFBox
- Apache Tika

### Development & DevOps

- Git
- GitHub
- Docker
- GitHub Actions
- Swagger / OpenAPI

---

## 📦 Core Modules

### 1. Syllabus Ingestion

Upload a syllabus document and extract its content.

```text
PDF / DOCX
    ↓
Text Extraction
    ↓
AI Structuring
    ↓
Subject → Unit → Topic
```

### 2. Skill Embedding & Taxonomy

Convert curriculum topics and industry skills into vector embeddings and perform semantic comparison.

### 3. Job Market Intelligence

Collect job postings for selected roles and identify the technical skills required by industry.

### 4. Skill Matching & Gap Detection

Compare curriculum skills with industry requirements and identify:

- Covered skills
- Partially covered skills
- Missing skills
- High-demand skill gaps

### 5. AI Analysis & Recommendations

Use AI to analyze identified gaps and generate actionable curriculum recommendations.

### 6. Analytics Dashboard

Provide visual representations of:

- Curriculum coverage
- Industry demand
- Skill gaps
- Skill priorities
- AI recommendations

### 7. Authentication & Authorization

Role-based access for:

- ADMIN
- DEPT_HEAD
- VIEWER

### 8. Reports

Generate structured curriculum-industry analysis reports.

---

## 🗄️ Core Database Design

```text
syllabus
   │
   └── subject
          │
          └── unit
                 │
                 └── topic
                        │
                        └── topic_embedding

job_posting
   │
   └── job_required_skill

gap_report
   │
   └── gap_item

report
   │
   └── report_recommendation

app_user
```

---

## 🔐 Security

CampusPulse uses role-based authentication and authorization.

| Role | Access |
|---|---|
| ADMIN | Manage users and system operations |
| DEPT_HEAD | Manage syllabus and view reports |
| VIEWER | View reports |

---

## 📊 Example Analysis

For a target role such as:

```text
Java Spring Boot Developer
```

CampusPulse may identify industry skills such as:

```text
Java
Spring Boot
REST API
Docker
AWS
Kafka
CI/CD
PostgreSQL
```

The system then compares these requirements against the uploaded curriculum.

Example result:

```text
Java             → MATCHED
Spring Boot      → MATCHED
REST API         → MATCHED
PostgreSQL       → MATCHED
Docker           → GAP
AWS              → GAP
Kafka             → GAP
CI/CD            → GAP
```

The system then prioritizes the gaps according to their industry demand.

---

## 📅 Development Roadmap

The project is being developed incrementally.

### Phase 1 — Foundation

- Spring Boot initialization
- PostgreSQL configuration
- Database architecture
- Authentication
- REST API structure

### Phase 2 — Syllabus Intelligence

- Syllabus upload
- PDF/DOCX parsing
- AI syllabus structuring
- Curriculum database

### Phase 3 — Job Market Intelligence

- Job data integration
- Job description processing
- Industry skill extraction

### Phase 4 — Semantic Matching

- Embedding generation
- pgvector integration
- Similarity matching
- Skill taxonomy

### Phase 5 — AI Gap Analysis

- Gap detection
- Demand-based prioritization
- AI analysis
- AI recommendations

### Phase 6 — Dashboard

- React frontend
- Charts
- Gap visualization
- Recommendation interface
- Report generation

### Phase 7 — Testing & Deployment

- Unit testing
- Integration testing
- Docker
- CI/CD
- Deployment
- Documentation

---

## 📁 Project Structure

```text
CampusPulse/
│
├── backend/
│
├── frontend/
│
├── database/
│
├── docs/
│
├── docker/
│
├── .gitignore
├── LICENSE
└── README.md
```

---

## 🎓 Academic Project

**Project:** CampusPulse  
**Type:** MCA Semester Project  
**Domain:** Artificial Intelligence / Education Technology / Data Analytics  
**Architecture:** Modular Monolith  
**Backend:** Java Spring Boot  
**Frontend:** React  
**Database:** PostgreSQL + pgvector

---

## 👨‍💻 Development Status

🚧 **Currently Under Development**

The project is being developed incrementally, starting with the Spring Boot backend and syllabus intelligence module.

---

## 📌 Future Scope

Potential future enhancements include:

- Multi-university curriculum comparison
- Automated curriculum evolution tracking
- Student skill profiling
- Personalized learning-path generation
- University-wide analytics
- Industry partnership insights
- Microservices-based deployment
- Advanced AI curriculum planning

---

## 📄 License

This project is licensed under the MIT License. See the `LICENSE` file for details.
