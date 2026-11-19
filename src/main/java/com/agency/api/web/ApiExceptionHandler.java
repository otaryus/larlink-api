package com.agency.api.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger; import org.slf4j.LoggerFactory; import org.springframework.http.*; import org.springframework.security.access.AccessDeniedException; import org.springframework.web.bind.MethodArgumentNotValidException; import org.springframework.web.bind.annotation.*; import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<?> invalid(MethodArgumentNotValidException e){var fields=new LinkedHashMap<String,String>();e.getBindingResult().getFieldErrors().forEach(err->{String message=err.getDefaultMessage();if(message!=null&&message.contains("size must be between")){int separator=message.indexOf("and ");String limit=separator>=0?message.substring(separator+4).trim():"";message="O valor introduzido é demasiado curto.";if(limit.isEmpty()||err.getField().equals("name"))message="O nome deve ter pelo menos 4 caracteres.";else if(err.getField().equals("address"))message="Indique o endereço completo com pelo menos 5 caracteres.";}fields.putIfAbsent(err.getField(),message==null?"Dados inválidos.":message);});return ResponseEntity.badRequest().body(Map.of("error","Dados inválidos.","fields",fields));}
    @ExceptionHandler(AccessDeniedException.class) public ResponseEntity<?> forbidden(AccessDeniedException e){return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error","A sua conta não tem permissão para esta operação."));}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> businessRule(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));}
    @ExceptionHandler(Exception.class) public ResponseEntity<?> unexpected(Exception e, HttpServletRequest request){log.error("Unhandled API error method={} path={}",request.getMethod(),request.getRequestURI(),e);return ResponseEntity.status(500).body(Map.of("error","O servidor não conseguiu concluir a operação. Tente novamente e, se persistir, contacte a agência."));}
}
