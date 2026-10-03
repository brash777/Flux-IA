package com.fluxia.backend.category;

import com.fluxia.backend.shared.ApiException;
import com.fluxia.backend.transaction.TransactionRepository;
import com.fluxia.backend.user.User;
import com.fluxia.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Gestion de categorias.
 *
 * <p>Las siete del sistema (las que el prototipo mostraba como chips)
 * las crea la migracion V2 y son de solo lectura. Un usuario puede
 * agregar las suyas, pero no tocar las compartidas: si pudiera
 * renombrar "Comida", se la cambiaria a todos.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public CategoryService(CategoryRepository categoryRepository,
                           TransactionRepository transactionRepository,
                           UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryDtos.Response> list(UUID userId) {
        return categoryRepository.findVisibleTo(userId).stream()
                .map(CategoryDtos.Response::from)
                .toList();
    }

    @Transactional
    public CategoryDtos.Response create(UUID userId, CategoryDtos.CreateRequest request) {
        String slug = toSlug(request.name());

        // Se comprueba contra las visibles, que incluyen las del
        // sistema: si no, el indice unico parcial de la V2 dejaria
        // pasar una categoria propia llamada igual que una compartida.
        boolean alreadyVisible = categoryRepository.findBySlugVisibleTo(slug, userId).stream()
                .anyMatch(existing -> existing.getSlug().equals(slug));

        if (alreadyVisible) {
            throw ApiException.conflict("CATEGORY_EXISTS",
                    "Ya tienes una categoria llamada '" + request.name() + "'.");
        }

        User user = userRepository.getReferenceById(userId);
        Category category = new Category(
                user,
                slug,
                request.name().trim(),
                request.icon() != null ? request.icon() : "",
                request.color() != null ? request.color() : "#6c63ff",
                request.kind());

        return CategoryDtos.Response.from(categoryRepository.save(category));
    }

    /**
     * Borra una categoria propia.
     *
     * <p>Se rechaza si tiene movimientos. Borrarla en cascada
     * eliminaria transacciones reales, y eso cambiaria el saldo del
     * usuario sin que el lo haya pedido.
     */
    @Transactional
    public void delete(UUID userId, UUID categoryId) {
        Category category = categoryRepository.findByIdVisibleTo(categoryId, userId)
                .orElseThrow(() -> ApiException.notFound("La categoria no existe."));

        if (category.isSystemCategory()) {
            throw ApiException.badRequest("CATEGORY_IS_SYSTEM",
                    "Las categorias del sistema no se pueden eliminar.");
        }
        if (transactionRepository.existsByCategoryId(categoryId)) {
            throw ApiException.badRequest("CATEGORY_IN_USE",
                    "La categoria tiene movimientos asociados. Reasignalos antes de eliminarla.");
        }

        categoryRepository.delete(category);
    }

    /**
     * Convierte un nombre en identificador: "Café del Centro" pasa a
     * "cafe-del-centro".
     *
     * <p>La normalizacion NFD separa las letras de sus acentos, y el
     * reemplazo siguiente borra los acentos sueltos. Sin eso, "Cafe" y
     * "Café" generarian dos slugs distintos para la misma categoria.
     */
    private String toSlug(String name) {
        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        if (normalized.isBlank()) {
            throw ApiException.badRequest("CATEGORY_NAME_INVALID",
                    "El nombre debe tener al menos una letra o numero.");
        }
        return normalized.length() > 40 ? normalized.substring(0, 40) : normalized;
    }
}
