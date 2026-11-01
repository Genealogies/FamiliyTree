package com.hannes.familytree.members

data class TreeSetting(
    val key: String,
    val label: String,
    val default: String = "",
    val choices: List<String> = emptyList(),
    val toggle: Boolean = false,
    val minimum: Int? = null
)

object TreeSettings {
    private fun flag(key: String, label: String, enabled: Boolean = false) =
        TreeSetting(key, label, if (enabled) "on" else "", toggle = true)
    private fun choice(key: String, label: String, default: String, vararg choices: String) =
        TreeSetting(key, label, default, choices.toList())
    private fun number(key: String, label: String, default: Int, minimum: Int = 0) =
        TreeSetting(key, label, default.toString(), minimum = minimum)
    private fun border(prefix: String, colors: Boolean = false): List<TreeSetting> = listOf(
        number("$prefix.width", "Border Width", 1),
        choice("$prefix.style", "Border Style", "solid", "none", "solid", "dashed", "dotted", "double"),
        number("$prefix.radius", "Border Radius", 4)
    ) + if (colors) listOf("male", "female", "other").map {
        TreeSetting("$prefix.color.$it", "${it.replaceFirstChar(Char::uppercaseChar)} Border Color", "#808080")
    } else listOf(TreeSetting("$prefix.color", "Border Color", "#808080"))
    private fun text(prefix: String): List<TreeSetting> = listOf(
        TreeSetting("$prefix.font_family", "Font Family", "Default"),
        number("$prefix.font_size", "Font Size", 14, 1),
        choice("$prefix.font_weight", "Font Weight", "400", "100", "200", "300", "400", "500", "600", "700", "800", "900"),
        choice("$prefix.font_style", "Font Style", "normal", "normal", "italic"),
        TreeSetting("$prefix.color", "Font Color", "#000000"),
        choice("$prefix.align", "Text Align", "center", "left", "center", "right")
    )

    val sections: Map<String, List<TreeSetting>> = linkedMapOf(
        "General Setting" to listOf(
            flag("root_highlight", "Highlight Root"),
            flag("gt_frontend", "GT Frontend"),
            choice("style", "Select Style", "1", "1", "2", "2-alt", "3", "3-alt", "4", "5"),
            choice("layout", "Select Layout", "vr", "vr", "hr"),
            flag("ajax", "Enable Ajax"), flag("popup", "Enable Popup"),
            flag("female_tree", "Hide Female Tree"), flag("hide_spouse", "Hide Spouse"),
            flag("hide_un_spouse", "Hide Unknown Spouse"),
            flag("collapsible_family_root", "Collapsible Family: Root"),
            flag("collapsible_family_spouse", "Collapsible Family: Spouse"),
            flag("collapsible_family_onload", "Collapse Onload"),
            TreeSetting("background.color", "Background Color", "#ffffff"),
            TreeSetting("marr_icon", "Marriage Icon")
        ),
        "Visibility Setting" to listOf(
            choice("name", "Name", "full", "title", "full", "first"),
            choice("birt", "Birth Day", "year", "full", "year", "none"),
            flag("birt_hide_alive", "Hide Birth Day Who Alive"),
            choice("deat", "Died", "year", "full", "year", "none"),
            choice("gender", "Show Gender", "none", "icon", "full", "short", "none"),
            choice("sibling_order", "Sibling Order", "default", "default", "oldest", "youngest"),
            flag("generation", "Show Generation"),
            number("generation_number", "Number Of Generation", -1, -1),
            flag("ancestor", "Show Ancestor", true),
            number("generation_number_ancestor", "Number Of Ancestor Generation", -1, -1),
            number("generation_start_from", "Generation Start From", 1),
            flag("treelink", "Show Tree Link")
        ),
        "Container Setting" to (listOf(
            TreeSetting("container.background.color", "Background Color", "#ffffff"),
            TreeSetting("container.background.image", "Background Image (CSS linear-gradient)"),
            TreeSetting("container.background.size", "Background Size", "cover")
        ) + border("container.border")),
        "Box Setting" to (listOf(
            choice("box.layout", "Layout", "vr", "vr", "hr"),
            TreeSetting("box.width", "Width", "150"),
            TreeSetting("box.background.color.male", "Male Background Color", "#e3f2fd"),
            TreeSetting("box.background.color.female", "Female Background Color", "#fce4ec"),
            TreeSetting("box.background.color.other", "Other Background Color", "#eeeeee")
        ) + border("box.border", true)),
        "Line Setting" to listOf(
            number("line.border.width", "Line Size", 1),
            choice("line.border.style", "Line Style", "solid", "solid", "dashed", "dotted"),
            number("line.border.radius", "Line Corner Radius", 0),
            TreeSetting("line.border.color", "Line Color", "#808080")
        ),
        "Image Setting" to (listOf(
            flag("thumb.show", "Show Image", true),
            number("thumb.width", "Width", 80, 1)
        ) + border("thumb.border", true)),
        "Name Text Setting" to text("name_text"),
        "Other Text Setting" to text("other_text")
    )

    fun value(tree: Tree, key: String): String =
        tree.settings[key] ?: sections.values.flatten().first { it.key == key }.default

    fun validate(settings: Map<String, String>) {
        sections.values.flatten().forEach { field ->
            val value = settings[field.key] ?: field.default
            require(field.choices.isEmpty() || value in field.choices) { "Invalid ${field.label}" }
            require(!field.toggle || value == "" || value == "on") { "Invalid ${field.label}" }
            field.minimum?.let { minimum ->
                require(value.toIntOrNull()?.let { it >= minimum } == true) {
                    "${field.label} must be a whole number >= $minimum"
                }
            }
            if (field.key.endsWith(".color") || field.key.contains(".color.")) {
                require(Regex("#[0-9a-fA-F]{6}").matches(value)) {
                    "${field.label} must be a six-digit hex color, e.g. #ffffff"
                }
            }
        }
    }
}
