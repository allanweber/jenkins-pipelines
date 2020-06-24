def call(Map pipelineParams) {
    pipeline {
        agent any
        environment {
            String committer = ''
            String envType = ''
            String version = ''
            String image = ''
            String prd = 'prd'
            String master = 'master'
        }
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
                        version = sh(returnStdout: true, script: 'mvn help:evaluate -Dexpression=project.version -q -DforceStdout')
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

            stage('Sonar') {
                when {
                    not {
                        branch master
                    }
                }
                steps {
                    echo 'run sonarQube in future'
                }
            }

            stage('Build Image') {
                steps {
                    script {
                        sh "docker build --build-arg ENV_ARG=${envType} -t ${image} ."
                    }
                }
            }

            stage('Docker Login') {
                steps {
                    script {
                        echo "${env.DOCKER_TOKEN} | docker login -u ${env.DOCKER_USER} --password-stdin"
                    }
                }
            }

            stage('Push Images') {
                parallel {
                    stage('Push Current Image') {
                        steps {
                            script {
                                pushImage(image)
                                removeImage(image)
                            }
                        }
                    }
                    stage ('Push Latest Image') {
                        when {
                            branch master
                        }
                        steps {
                            script {
                                String latestImage = "${imageBaseName}:latest"
                                sh "docker tag ${image} ${latestImage}"
                                pushImage(latestImage)
                                removeImage(latestImage)
                            }
                        }
                    }
                }
            }
        }
    }
}

def pushImage(imageName) {
    sh "docker push ${imageName}"
}

def removeImage(imageName) {
    sh "docker rmi ${imageName}"
}