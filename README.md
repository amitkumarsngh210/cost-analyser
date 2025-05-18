# Costwise - AWS Cost Analysis and Optimization Tool

Costwise is a powerful tool that analyzes AWS cost usage and generates optimization suggestions. It helps organizations identify potential cost savings and optimize their AWS infrastructure.

## Features

- AWS Cost Explorer integration for detailed cost analysis
- Automated cost optimization suggestions
- Excel report generation with actionable insights
- Secure API key-based authentication
- Dockerized deployment
- CI/CD pipeline with GitHub Actions

## Tech Stack

- Java 17
- Spring Boot 3.2.3
- MySQL 8.0
- AWS SDK for Java
- Apache POI for Excel generation
- Docker & Docker Compose

## Prerequisites

- Java 17 or later
- Maven 3.9 or later
- Docker and Docker Compose
- AWS Account with Cost Explorer access
- MySQL 8.0 (if running without Docker)

## Quick Start

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/costwise.git
   cd costwise
   ```

2. Configure AWS credentials:
   ```bash
   export AWS_ACCESS_KEY=your_access_key
   export AWS_SECRET_KEY=your_secret_key
   export AWS_REGION=your_region
   ```

3. Run with Docker Compose:
   ```bash
   docker-compose up -d
   ```

4. Access the API:
   ```
   http://localhost:8080/api
   ```

## API Endpoints

- `POST /api/analyze/{accountId}` - Trigger cost analysis
- `GET /api/analyze/{runId}` - Get analysis results
- `GET /api/reports/{runId}` - Download Excel report

## Development

1. Build the project:
   ```bash
   mvn clean package
   ```

2. Run tests:
   ```bash
   mvn test
   ```

3. Run locally:
   ```bash
   mvn spring-boot:run
   ```

## Docker Deployment

Build and run the Docker image:
```bash
docker build -t costwise .
docker run -p 8080:8080 costwise
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Security

- AWS credentials are encrypted in the database
- API key authentication required for all endpoints
- HTTPS recommended for production deployment

## Support

For support, please open an issue in the GitHub repository.
