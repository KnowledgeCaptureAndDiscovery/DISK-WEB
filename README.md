
# DISK

The DISK system automates the execution of scientific workflows triggered 
on data changes. To do this DISK collects data from different data repositories
and defines methods on different workflows systems. User defined goals are 
periodically check for new data/methods available. When a method detects new data,
a new workflow execution will be send. Each experiment execution is stored with its
metadata and outputs for posterior analysis.

## Installation

You can install DISK using Docker or [building from source](./building.md)
We recommend to use `docker` to install DISK. 


### Docker

Install DISK with docker

```bash
docker-compose up -d
```


## Usage

### Check the server

Open http://localhost:8080/disk-server/vocabulary to check that the local repository server is working fine. It might take a little while to open it for the first time as it downloads vocabularies from the internet.

### Check the client

Open http://localhost:8000/index.html to access the Disk UI that connects with the local repository


## Documentation

Full documentation is available at [https://disk.readthedocs.io/en/latest/](https://disk.readthedocs.io/en/latest/)
