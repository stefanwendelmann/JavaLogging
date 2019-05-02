
# Java Loggging Test with Log4j2

## Requires

- Create a MS SQL Database on version 2014 or higher (for the Flyway SQL Scripts)
- Edit the src/test/resources/log4j2-test.xml DB infos 
- Edit the pom.xml with the same DB infos

## Run the Test

Info: Creates the DB if not exist & cleans the DB content

```bash
mvn clean test -Prefresh-db
```

