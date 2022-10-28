package no.difi.meldingsutveksling.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;

public class InvalidKorrespondanseparttypeException extends HttpStatusCodeException {

    public InvalidKorrespondanseparttypeException() {
        super(HttpStatus.BAD_REQUEST, InvalidKorrespondanseparttypeException.class.getName());
    }
}
