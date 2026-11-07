# T-E6-19 (RNF-28): sqlmap in a throwaway image, so nothing is installed on the machine. Pinned, so a run can be
# repeated. e2e/security/sqlmap.ts builds it on first use and runs it.
FROM python:3.13-alpine
RUN pip install --no-cache-dir sqlmap==1.10.11
# sqlmap writes its session files and results here
WORKDIR /tmp
ENTRYPOINT ["sqlmap"]
