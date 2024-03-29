
INSERT INTO public.software_package (id, vendor, name, notable, description, external_id, provenance, created_by, created_at, "group")
VALUES
(1, 'Apache', 'log4j', false, 'Logging library for Java', 'log4j', 'sample_artifactory', 'admin', now(), 'log4j_group'),
(2, 'Apache Software', 'Apache Maven', false, 'Build automation and project management tool', 'apache_maven', 'sample_artifactory', 'admin', now(), 'maven_group'),
(3, 'JUnit Team', 'JUnit', false, 'Testing framework for Java', 'junit', 'sample_artifactory', 'admin', now(), 'junit_group'),
(4, 'Pivotal', 'Spring Framework', false, 'Comprehensive framework for Java', 'spring_framework', 'sample_artifactory', 'admin', now(), 'spring_group'),
(5, 'Docker Inc.', 'Docker', false, 'Containerization platform', 'docker', 'sample_artifactory', 'admin', now(), 'docker_group'),
(6, 'Node.js Foundation', 'Node.js', false, 'JavaScript runtime for server-side development', 'node_js', 'sample_artifactory', 'admin', now(), 'nodejs_group'),
(7, 'Python Software Foundation', 'Python packages', false, 'Various Python libraries and packages', 'python_packages', 'sample_artifactory', 'admin', now(), 'python_group'),
(8, 'Google', 'AngularJS', false, 'Front-end JavaScript framework', 'angularjs', 'sample_artifactory', 'admin', now(), 'angularjs_group'),
(9, 'Facebook', 'React', false, 'Front-end JavaScript library', 'react', 'sample_artifactory', 'admin', now(), 'react_group'),
(10, 'Evan You', 'Vue.js', false, 'Front-end JavaScript framework', 'vue_js', 'sample_artifactory', 'admin', now(), 'vuejs_group'),
(11, 'Apache Software', 'Apache Tomcat', false, 'Servlet container for Java', 'apache_tomcat', 'sample_artifactory', 'admin', now(), 'tomcat_group'),
(12, 'Oracle', 'MySQL Connector/J', false, 'JDBC driver for MySQL database', 'mysql_connector_j', 'sample_artifactory', 'admin', now(), 'mysql_group'),
(13, 'Red Hat', 'Hibernate', false, 'Object-relational mapping framework for Java', 'hibernate', 'sample_artifactory', 'admin', now(), 'hibernate_group'),
(14, 'Jenkins', 'Jenkins plugins', false, 'Extensions for the Jenkins automation server', 'jenkins_plugins', 'sample_artifactory', 'admin', now(), 'jenkins_group'),
(15, 'Apache Software', 'Apache Kafka', false, 'Distributed streaming platform', 'apache_kafka', 'sample_artifactory', 'admin', now(), 'kafka_group'),
(16, 'Elastic', 'Elasticsearch', false, 'Distributed search and analytics engine', 'elasticsearch', 'sample_artifactory', 'admin', now(), 'elasticsearch_group'),
(17, 'Pivotal', 'RabbitMQ', false, 'Message broker for distributed systems', 'rabbitmq', 'sample_artifactory', 'admin', now(), 'rabbitmq_group'),
(18, 'Git', 'Git', false, 'Version control system', 'git', 'sample_artifactory', 'admin', now(), 'git_group'),
(19, 'Red Hat', 'Ansible', false, 'Automation tool for configuration management', 'ansible', 'sample_artifactory', 'admin', now(), 'ansible_group'),
(20, 'Nginx', 'Nginx', false, 'Web server and reverse proxy server', 'nginx', 'sample_artifactory', 'admin', now(), 'nginx_group'),
(21, 'Apache Software', 'Apache HTTP Server', false, 'Open-source web server', 'apache_http_server', 'sample_artifactory', 'admin', now(), 'apache_group'),
(22, 'Google', 'Android SDK', false, 'Software development kit for Android app dev', 'android_sdk', 'sample_artifactory', 'admin', now(), 'android_group');
