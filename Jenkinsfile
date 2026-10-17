// =============================================================================
// StockSmart — Jenkins CI/CD pipeline (Day 7)
//   Checkout → backend build+tests → frontend build (+specs when a browser
//   exists) → Docker images → Compose config validation → optional local deploy.
//
// Jenkins prerequisites (configured on the agent/controller, not in this file):
//   - Tools: jdk-17 (Temurin 17) and node-20, matching the `tools` block.
//   - Maven on PATH (or a Maven tool) for `mvn`.
//   - Docker Engine + `docker compose` (v2) available to the agent user.
//   - No registry credentials are required: images are built locally only.
//     Registry publishing / remote deploy is intentionally NOT configured.
//
// The pipeline never needs real application secrets: tests run on H2, and the
// compose validation stage uses obvious CI placeholders.
// =============================================================================
pipeline {
    agent any

    tools {
        jdk 'jdk-17'
        nodejs 'node-20'
    }

    options {
        timestamps()
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    parameters {
        booleanParam(
            name: 'DEPLOY_LOCAL',
            defaultValue: false,
            description: 'Start docker compose on this agent and run smoke tests after the build')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Backend build + test') {
            steps {
                dir('backend') {
                    // `verify` runs the full surefire suite (H2 test profile) and
                    // packages the boot jar. No Oracle needed for CI.
                    sh 'mvn -B -ntp clean verify'
                }
            }
        }

        stage('Frontend build') {
            steps {
                dir('frontend') {
                    sh 'npm ci --no-audit --no-fund'
                    sh 'npm run build'
                    // Angular unit tests need Chrome. Run them when the agent has
                    // a browser; otherwise say so loudly instead of pretending.
                    sh '''
                        if command -v google-chrome >/dev/null 2>&1 \
                           || command -v chromium >/dev/null 2>&1 \
                           || command -v chromium-browser >/dev/null 2>&1 \
                           || [ -n "${CHROME_BIN:-}" ]; then
                            npx ng test --watch=false --browsers=ChromeHeadless
                        else
                            echo "SKIP: no Chrome/CHROME_BIN on this agent — Angular unit tests not executed"
                        fi
                    '''
                }
            }
        }

        stage('Build Docker images') {
            steps {
                // Clear failure if Docker is missing on the agent.
                sh 'docker version'
                sh 'docker build -t "stocksmart-backend:${BUILD_NUMBER}" -t stocksmart-backend:latest ./backend'
                sh 'docker build -t "stocksmart-frontend:${BUILD_NUMBER}" -t stocksmart-frontend:latest ./frontend'
            }
        }

        stage('Validate Compose config') {
            steps {
                // Renders the compose model with CI placeholders (no real secrets).
                sh 'DB_PASSWORD=ci-placeholder JWT_SECRET=ci-placeholder docker compose config --quiet'
            }
        }

        stage('Deploy locally (opt-in)') {
            // Opt-in: requires Docker on the agent AND a .env in the workspace
            // (DB_PASSWORD + JWT_SECRET) — compose refuses to start without them.
            when {
                expression { return params.DEPLOY_LOCAL }
            }
            steps {
                sh 'docker compose up -d --build'
                # Smoke through the frontend edge (nginx proxy + SPA fallback) and
                # let the script wait for container readiness (no fixed sleep).
                sh 'scripts/smoke-test.sh http://localhost:4200'
                sh 'docker compose ps'
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'backend/target/surefire-reports/*.xml'
        }
        failure {
            echo 'StockSmart pipeline FAILED — inspect the first failing stage above.'
        }
        success {
            echo 'StockSmart pipeline PASSED — backend tests, frontend build, images, compose config.'
        }
    }
}
