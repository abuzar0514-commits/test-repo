import com.devops.ConfigParser

def call(String configFile = 'pipeline.conf') {
    def config = [:]

    pipeline {
        agent any

        options {
            ansiColor('xterm')
            timestamps()
        }

        stages {
            stage('Read Configuration') {
                steps {
                    script {
                        if (!fileExists(configFile)) {
                            error "Configuration file '${configFile}' not found in workspace."
                        }
                        def content = readFile(configFile)
                        config = ConfigParser.parseConfig(content)

                        echo "Loaded configuration for Environment: ${config['ENVIRONMENT']}"
                    }
                }
            }

            stage('Clone Repository') {
                steps {
                    script {
                        echo "Cloning repository from ${config['GIT_REPOSITORY_URL']} (Branch: ${config['GIT_BRANCH']})"
                        checkout([
                            $class: 'GitSCM',
                            branches: [[name: config['GIT_BRANCH'] ?: 'main']],
                            userRemoteConfigs: [[
                                url: config['GIT_REPOSITORY_URL'],
                                credentialsId: 'git-credentials-id'
                            ]]
                        ])
                    }
                }
            }

            stage('User Approval') {
                when {
                    expression { config['KEEP_APPROVAL_STAGE']?.toBoolean() == true }
                }
                steps {
                    script {
                        echo "Approval required for environment: ${config['ENVIRONMENT']}"
                        timeout(time: 1, unit: 'HOURS') {
                            input message: "${config['ACTION_MESSAGE'] ?: 'Approve deployment?'} [Env: ${config['ENVIRONMENT']}]",
                                  ok: "Approve",
                                  submitterParameter: 'APPROVED_BY'
                        }
                        echo "Approved by ${APPROVED_BY}"
                    }
                }
            }

            stage('Playbook Execution') {
                steps {
                    script {
                        dir(config['CODE_BASE_PATH'] ?: '.') {
                            echo "Executing Ansible Playbook: ${config['ANSIBLE_PLAYBOOK']}"
                            
                            ansiblePlaybook(
                                playbook: config['ANSIBLE_PLAYBOOK'] ?: 'site.yml',
                                inventory: config['ANSIBLE_INVENTORY'] ?: 'inventory/hosts.ini',
                                credentialsId: 'ec2-ssh-key-id',
                                colorized: true,
                                extras: '-v'
                            )
                        }
                    }
                }
            }
        }

        post {
            always {
                script {
                    def status = currentBuild.currentResult
                    def channel = config['SLACK_CHANNEL_NAME'] ?: 'build-status'
                    def message = "Vault Deployment - Status: ${status} (${env.BUILD_URL})"

                    echo "Notification -> Channel: #${channel} | Message: ${message}"
                }
            }
        }
    }
}
