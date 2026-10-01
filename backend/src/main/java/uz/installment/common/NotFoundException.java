package uz.installment.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String entity, Object id) {
        super(entity + " topilmadi: " + id);
    }
}
