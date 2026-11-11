package com.recipehub.bean;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Named("recipeBean")
@ViewScoped
public class RecipeBean implements Serializable {

    // ----------------------------------------------------
    // ตั้งค่าการเชื่อมต่อ MySQL Database
    // ----------------------------------------------------
    private static final String DB_URL = "jdbc:mysql://localhost:3306/food_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "529466";

    private String searchQuery = "";
    private int selectedCategory = 0;
    private List<Recipe> recipes = new ArrayList<>();
    private List<Recipe> filteredRecipes = new ArrayList<>();
    private List<Category> categories = new ArrayList<>();
    private List<Integer> bookmarks = new ArrayList<>();
    private Recipe selectedRecipe;

    private Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    @PostConstruct
    public void init() {
        loadCategories();
        loadRecipes();
    }

    // 1. ดึงหมวดหมู่ทั้งหมดจากฐานข้อมูล (categories)
    public void loadCategories() {
        categories = new ArrayList<>();
        String sql = "SELECT * FROM categories ORDER BY id ASC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                categories.add(new Category(rs.getInt("id"), rs.getString("name")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 2. ดึงสูตรอาหารทั้งหมดจากฐานข้อมูล (recipes + categories) และแปลงรูป BLOB เป็น Base64
    public void loadRecipes() {
        recipes = new ArrayList<>();
        String sql = "SELECT r.*, c.name AS category_name " +
                     "FROM recipes r " +
                     "LEFT JOIN categories c ON r.category_id = c.id " +
                     "ORDER BY r.id DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String title = rs.getString("title");
                String description = rs.getString("description");
                int categoryId = rs.getInt("category_id");
                String categoryName = rs.getString("category_name");
                
                // แปลงรูปภาพ BLOB เป็น Base64 String
                byte[] imgBytes = rs.getBytes("image");
                String imageStr = null;
                if (imgBytes != null && imgBytes.length > 0) {
                    imageStr = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imgBytes);
                }

                recipes.add(new Recipe(id, title, description, categoryId, categoryName, imageStr));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        filterRecipes();
    }

    // 3. ระบบค้นหาและกรองสูตรอาหาร
    public void filterRecipes() {
        filteredRecipes = new ArrayList<>();
        for (Recipe r : recipes) {
            boolean matchSearch = searchQuery == null || searchQuery.trim().isEmpty() 
                    || r.getTitle().toLowerCase().contains(searchQuery.toLowerCase().trim());
            boolean matchCat = (selectedCategory == 0) || (r.getCategoryId() == selectedCategory);

            if (matchSearch && matchCat) {
                filteredRecipes.add(r);
            }
        }
    }

    // 4. ดึงรายละเอียดสูตรอาหาร (วัตถุดิบ + วิธีทำ) เมื่อคลิกเปิด Modal
    public void selectRecipe(int recipeId) {
        for (Recipe r : recipes) {
            if (r.getId() == recipeId) {
                this.selectedRecipe = r;
                loadRecipeDetails(this.selectedRecipe);
                break;
            }
        }
    }

    private void loadRecipeDetails(Recipe recipe) {
        recipe.getIngredients().clear();
        recipe.getSteps().clear();

        // ดึงวัตถุดิบ (recipe_ingredients + ingredients)
        String sqlIng = "SELECT i.name, ri.amount " +
                        "FROM recipe_ingredients ri " +
                        "JOIN ingredients i ON ri.ingredient_id = i.id " +
                        "WHERE ri.recipe_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlIng)) {
            stmt.setInt(1, recipe.getId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                recipe.getIngredients().add(new Ingredient(rs.getString("name"), rs.getString("amount")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // ดึงวิธีทำ (recipe_steps)
        String sqlSteps = "SELECT instruction FROM recipe_steps WHERE recipe_id = ? ORDER BY step_number ASC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlSteps)) {
            stmt.setInt(1, recipe.getId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                recipe.getSteps().add(rs.getString("instruction"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 5. ระบบสลับบันทึกสูตรโปรด (Bookmark)
    public void toggleBookmark(int recipeId) {
        if (bookmarks.contains(recipeId)) {
            bookmarks.remove(Integer.valueOf(recipeId));
        } else {
            bookmarks.add(recipeId);
        }
    }

    public boolean isBookmarked(int recipeId) {
        return bookmarks.contains(recipeId);
    }

    // ----------------------------------------------------
    // Model Classes
    // ----------------------------------------------------
    public static class Recipe implements Serializable {
        private int id;
        private String title;
        private String description;
        private int categoryId;
        private String categoryName;
        private String image;
        private List<Ingredient> ingredients = new ArrayList<>();
        private List<String> steps = new ArrayList<>();

        public Recipe(int id, String title, String description, int categoryId, String categoryName, String image) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.image = image;
        }

        public int getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public int getCategoryId() { return categoryId; }
        public String getCategoryName() { return categoryName; }
        public String getImage() { return image; }
        public List<Ingredient> getIngredients() { return ingredients; }
        public List<String> getSteps() { return steps; }
    }

    public static class Category implements Serializable {
        private int id;
        private String name;
        public Category(int id, String name) { this.id = id; this.name = name; }
        public int getId() { return id; }
        public String getName() { return name; }
    }

    public static class Ingredient implements Serializable {
        private String name;
        private String amount;
        public Ingredient(String name, String amount) { this.name = name; this.amount = amount; }
        public String getName() { return name; }
        public String getAmount() { return amount; }
    }

    // Getters and Setters
    public String getSearchQuery() { return searchQuery; }
    public void setSearchQuery(String searchQuery) { this.searchQuery = searchQuery; }
    public int getSelectedCategory() { return selectedCategory; }
    public void setSelectedCategory(int selectedCategory) { this.selectedCategory = selectedCategory; }
    public List<Recipe> getFilteredRecipes() { return filteredRecipes; }
    public List<Category> getCategories() { return categories; }
    public Recipe getSelectedRecipe() { return selectedRecipe; }
}