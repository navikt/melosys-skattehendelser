FROM gcr.io/distroless/java17-debian12:nonroot
LABEL maintainer="Team Melosys"
COPY /build/libs/melosys-skattehendelser.jar /app/app.jar
ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ='Europe/Oslo'
CMD ["/app/app.jar"]
