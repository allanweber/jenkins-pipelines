/**
 Execute a common build pipeline for Java application, building sa docker image and publishing it
 @config is a map with some properties:
 -> imageBaseName: [String:Required] docker image name with owner. ex.: allanweber/java-app
 -> runSonar: [boolean:Required] indication to run Sonar Lint, if the Branch is master will not run sonar
 -> addEnvName: [boolean:Required] indication that should add endName (prd, dev, etc...) to the image name for non Master/Production environment
 */
def call(Map config) {
    pipeline {
        agent any
        environment {
            String committer = ''
            String envType = ''
            String version = ''
            String imageTag = ''
            String master = 'master'
            def appImage = ''
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
                        envType = getEnvType()
                        imageTag = getImageTag(config)
                    }
                    echo "-> Building for ${envType} environment"
                    echo "-> Project version: ${version}"
                    echo "-> Image tag: ${imageTag}"
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
                    expression {
                        return executeSonar(config)
                    }
                }
                steps {
                    echo 'run sonarQube in the future'
                }
            }

            stage('Build Image') {
                steps {
                    script {
                        docker.withRegistry('', 'DockerHub') {
                            appImage = docker.build(config.imageBaseName)
                        }
                    }
                }
            }

            stage('Push Images') {
                parallel {
                    stage('Push current tag') {
                        steps {
                            script {
                                appImage.push(imageTag)
                            }
                        }
                    }
                    stage ('Push Latest') {
                        when {
                            branch master
                        }
                        steps {
                            script {
                                appImage.push("latest")
                            }
                        }
                    }
                }
            }

            stage('Delete Images') {
                parallel {
                    stage('Delete current tag') {
                        steps {
                            script {
                                removeImage("${config.imageBaseName}:${imageTag}")
                            }
                        }
                    }
                    stage ('Delete Latest') {
                        when {
                            branch master
                        }
                        steps {
                            script {
                                removeImage("${config.imageBaseName}:latest")
                            }
                        }
                    }
                }
            }

            // stage('Build Image') {
            //     steps {
            //         script {
            //             sh "docker build --build-arg ENV_ARG=${envType} -t ${image} ."
            //         }
            //     }
            // }

            // stage('Docker Login') {
            //     steps {
            //         script {
            //             echo "${env.DOCKER_TOKEN} | docker login -u ${env.DOCKER_USER} --password-stdin"
            //         }
            //     }
            // }

            // stage('Push Images') {
            //     parallel {
            //         stage('Push Current Image') {
            //             steps {
            //                 script {
            //                     pushImage(image)
            //                     removeImage(image)
            //                 }
            //             }
            //         }
            //         stage ('Push Latest Image') {
            //             when {
            //                 branch master
            //             }
            //             steps {
            //                 script {
            //                     String latestImage = "${config.imageBaseName}:latest"
            //                     sh "docker tag ${image} ${latestImage}"
            //                     pushImage(latestImage)
            //                     removeImage(latestImage)
            //                 }
            //             }
            //         }
            //     }
            // }
        }
    }
}

String getImageTag(config) {
    String tag = null
    String envType = getEnvType()
    if (env.BRANCH_NAME == master) {
        tag = "${version}"
    }
    else {
        String envName = config.addEnvName ? "-${envType}" : ''
        tag = "${version}${envName}-${env.BUILD_ID}"
    }
    return tag
}

String getEnvType() {
    if (env.BRANCH_NAME == master) {
        return 'prd'
    } else {
        return 'dev'
    }
}

boolean executeSonar(config) {
    return config.runSonar && env.BRANCH_NAME != 'master';
}

def removeImage(imageName) {
    sh "docker rmi ${imageName} -f"
}