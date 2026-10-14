# AWS LocalStack FileService

A Spring Boot file catalog backed by LocalStack S3, SQS, and DynamoDB, with a Java 17 Lambda function for asynchronous S3 metadata inspection.

## Processing flow

1. Upload API stores the object in the configured S3 bucket.
2. File metadata is saved in DynamoDB.
3. The application sends the S3 bucket and key to the SQS queue.
4. An SQS event source mapping invokes the Lambda handler.
5. The Lambda calls S3 HeadObject and logs content type, size, last-modified time, and ETag.

The Spring SQS poller is disabled by default so it does not compete with the Lambda mapping.

## Requirements

- Java 17
- Docker Desktop using Linux containers
- LocalStack auth token
- AWS CLI v2
- Windows Command Prompt (cmd.exe)

## Start LocalStack

Create a named volume once to persist service state across container restarts:

~~~cmd
docker volume create localstack-data
~~~

Load your token from a local file. Do not put the token in this README or source control:

~~~cmd
set /p LOCALSTACK_AUTH_TOKEN=<"C:\path\to\localstack-token.txt"
~~~

Start LocalStack:

~~~cmd
docker run -d --name localstack --restart unless-stopped -p 4566:4566 -p 4571:4571 -e LOCALSTACK_AUTH_TOKEN=%LOCALSTACK_AUTH_TOKEN% -e PERSISTENCE=1 -e SQS_ENDPOINT_STRATEGY=domain -v localstack-data:/var/lib/localstack -v /var/run/docker.sock:/var/run/docker.sock localstack/localstack
~~~

The Docker socket mount is required to run Lambda containers. The restart policy starts LocalStack again when Docker Engine restarts. Port 4566 is the gateway; 4571 is retained for compatibility.

Useful container commands:

~~~cmd
docker ps -a
docker logs -f localstack
docker stop localstack
docker start localstack
docker volume inspect localstack-data
~~~

If a container named localstack already exists, use docker start if its mounts and environment are correct. Recreating it requires preserving the data volume and token configuration.

## Configure AWS CLI

These local placeholder credentials are for LocalStack, not AWS:

~~~cmd
aws configure set aws_access_key_id test --profile localstack
aws configure set aws_secret_access_key test --profile localstack
aws configure set region us-east-1 --profile localstack
~~~

Include the LocalStack endpoint in every AWS CLI command:

~~~cmd
--profile localstack --endpoint-url=http://localhost:4566 --region us-east-1
~~~

## Create AWS resources

Run these once for a fresh LocalStack data volume. Skip resources that already exist.

Create the bucket:

~~~cmd
aws --profile localstack --endpoint-url=http://localhost:4566 s3 mb s3://document-hub
~~~

Create the queue and note the QueueUrl returned:

~~~cmd
aws --profile localstack --endpoint-url=http://localhost:4566 --region us-east-1 sqs create-queue --queue-name file-processing-queue
~~~

With SQS_ENDPOINT_STRATEGY=domain, the application queue URL is:

~~~properties
app.aws.sqs.queue-url=http://queue.localhost.localstack.cloud:4566/000000000000/file-processing-queue
~~~

If LocalStack returns a different URL, update app.aws.sqs.queue-url in src/main/resources/application.properties to match.

Create the DynamoDB table:

~~~cmd
aws --profile localstack --endpoint-url=http://localhost:4566 --region us-east-1 dynamodb create-table --table-name file-metadata --attribute-definitions AttributeName=fileId,AttributeType=S --key-schema AttributeName=fileId,KeyType=HASH --billing-mode PAY_PER_REQUEST
~~~

Verify the resources:

~~~cmd
aws --profile localstack --endpoint-url=http://localhost:4566 s3 ls
aws --profile localstack --endpoint-url=http://localhost:4566 --region us-east-1 sqs list-queues
aws --profile localstack --endpoint-url=http://localhost:4566 --region us-east-1 dynamodb list-tables
~~~

## Build and deploy the Lambda

From the project root, build the package with the Maven wrapper:

~~~cmd
cd /d "D:\Java Sample Projects\S3Sample"
mvnw.cmd -f lambda-processor\pom.xml clean package
~~~

Create the Lambda from the package:

~~~cmd
cd /d "D:\Java Sample Projects\S3Sample\lambda-processor\target"
aws --profile localstack --endpoint-url=http://localhost:4566 --region us-east-1 lambda create-function --function-name s3-file-metadata-processor --runtime java17 --role arn:aws:iam::000000000000:role/lambda-role --handler com.ttnet.s3sample.lambda.S3FileMetadataHandler --zip-file fileb://file-processor-lambda.jar --timeout 30 --memory-size 512
aws --profile localstack --endpoint-url=http://localhost:4566 --region us-east-1 lambda wait function-active-v2 --function-name s3-file-metadata-processor
~~~

If the function already exists, update its code instead:

~~~cmd
aws --profile localstack --endpoint-url=http://localhost:4566 --region us-east-1 lambda update-function-code --function-name s3-file-metadata-processor --zip-file fileb://file-processor-lambda.jar
~~~

Verify function state:

~~~cmd
aws --profile localstack --endpoint-url=http://localhost:4566 --region us-east-1 lambda get-function-configuration --function-name s3-file-metadata-processor --query "{State:State,StateReason:StateReason}" --output table
~~~

## Connect SQS to Lambda

Create the event source mapping:

~~~cmd
aws --profile localstack --endpoint-url=http://localhost:4566 --region us-east-1 lambda create-event-source-mapping --function-name s3-file-metadata-processor --event-source-arn arn:aws:sqs:us-east-1:000000000000:file-processing-queue --batch-size 1 --enabled
~~~

Verify that the mapping reaches Enabled:

~~~cmd
aws --profile localstack --endpoint-url=http://localhost:4566 --region us-east-1 lambda list-event-source-mappings --function-name s3-file-metadata-processor --output table
~~~

Do not create a duplicate mapping if one already exists.

## Run the Spring Boot application

The configuration is in src/main/resources/application.properties. It points to LocalStack at http://localhost:4566, bucket document-hub, and the queue URL shown above.

~~~cmd
cd /d "D:\Java Sample Projects\S3Sample"
mvnw.cmd spring-boot:run
~~~

Keep app.aws.sqs.poller.enabled=false when the Lambda mapping is consuming the queue.

## REST API

All endpoints use /api/files.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | /api/files/upload | Upload multipart form field file |
| GET | /api/files | List file metadata |
| GET | /api/files/{id} | Get metadata by ID |
| GET | /api/files/{id}/download | Download a file by ID |
| DELETE | /api/files/{id} | Delete the S3 object and metadata |

Upload a test text file from another Command Prompt:

~~~cmd
echo Lambda smoke test> "%TEMP%\lambda-smoke.txt"
curl.exe -i -F "file=@%TEMP%\lambda-smoke.txt;type=text/plain" http://localhost:8080/api/files/upload
~~~

The response includes the generated S3 key. The app stores metadata and sends the bucket/key message to SQS; the Lambda then logs S3 metadata.

Read the Lambda log:

~~~cmd
aws --profile localstack --endpoint-url=http://localhost:4566 logs filter-log-events --log-group-name /aws/lambda/s3-file-metadata-processor --filter-pattern "Processed S3 object" --query "events[].message" --output text
~~~

List S3 objects and application metadata:

~~~cmd
aws --profile localstack --endpoint-url=http://localhost:4566 s3 ls s3://document-hub --recursive
curl.exe http://localhost:8080/api/files
~~~

## Project structure

- src/main/java: Spring Boot REST API and S3/SQS/DynamoDB integration
- src/main/resources/application.properties: LocalStack endpoint, test credentials, bucket, queue, and poller settings
- lambda-processor: Java SQS-triggered Lambda and its Maven build
- lambda-processor/target/file-processor-lambda.jar: generated deployment package

## Troubleshooting

- Port 4566 refuses connections: confirm Docker Desktop is running and the localstack container is healthy.
- Lambda enters Failed state: confirm the Docker socket mount and review docker logs localstack.
- SQS messages do not invoke Lambda: check that the event source mapping is Enabled and uses the correct queue ARN and region.
- Lambda cannot find an object: verify the upload completed and that the SQS message bucket/key match.
- ResourceAlreadyExists or QueueAlreadyExists: inspect the existing resource rather than creating a duplicate.
- AWS CLI appears to target AWS: include --endpoint-url=http://localhost:4566 on every command.

## Credential handling

The LocalStack auth token is intentionally omitted. Keep it in a local untracked file or environment variable; never commit it. The token was included in the supplied notes, so rotate it if it is still active.
