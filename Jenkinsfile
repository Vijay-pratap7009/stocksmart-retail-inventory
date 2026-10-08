// StockSmart — Day 1 CI pipeline (Jenkins-ready structure).
// Full containerized deployment lands in the later deployment/CI-CD phase.
pipeline {
    agent any

    tools {
        jdk 'jdk-17'
        nodejs 'node-20'
    }

    stages {
        stage('Backend build + test') {
            steps {
                dir('backend') {
                    // Tests run against the zero-dependency 'local' profile;
                    // Oracle-backed stages are added with the deployment phase.
                    sh 'mvn -B test'
                }
            }
        }
        stage('Frontend build') {
            steps {
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npx ng build'
                }
            }
        }
    }

    post {
        always {
            junit 'backend/target/surefire-reports/*.xml'
        }
    }
}
