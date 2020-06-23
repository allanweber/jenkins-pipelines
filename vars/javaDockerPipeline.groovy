def call(Map pipelineParams) {

    String  committer, envType, version, image
    // String imageBaseName = 'allanweber/miro-widgets'
    String prd = 'prd'
    String master = 'master'
    pipeline {
        agent any
        stages {
            stage ('Checking') {
                steps {
                    echo 'Checking Branch Build: ' + env.BRANCH_NAME
                    checkout scm
                    script {
                        committer = sh(returnStdout: true, script: 'git show -s --pretty=%an').trim()
                    }
                    echo 'committer -> ' + committer

                    script {
                        version = sh(returnStdout: true,
                        script: 'mvn help:evaluate -Dexpression=project.version -q -DforceStdout')
                    }
                    script {
                        if (env.BRANCH_NAME == master) {
                            envType = prd
                            image = "${pipelineParams.imageBaseName}:${version}"
                        }
                        else {
                            envType = 'dev'
                            image = "${pipelineParams.imageBaseName}:${version}-${envType}-${env.BUILD_ID}"
                        }
                    }
                    echo "Building for ${envType} environment"
                    echo 'project version: ' + version
                    echo 'image name: ' + image
                }
            }

            stage('Test') {
                steps {
                    sh 'mvn clean verify'
                    step([$class: 'JUnitResultArchiver', testResults: 'target/surefire-reports/TEST-*.xml'])
                }
            }
            // stage('checkout git') {
            //     steps {
            //         git branch: pipelineParams.branch, credentialsId: 'GitCredentials', url: pipelineParams.scmUrl
            //     }
            // }

            stage('build') {
                steps {
                    sh 'mvn clean package -DskipTests=true'
                }
            }
        }
    }
}