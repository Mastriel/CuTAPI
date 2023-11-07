package xyz.mastriel.brazil.items;

import kotlin.Unit;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import xyz.mastriel.brazil.BrazilPlugin;
import xyz.mastriel.brazil.BrazilPluginKt;
import xyz.mastriel.cutapi.item.CuTItemStack;
import xyz.mastriel.cutapi.item.CustomItem;
import xyz.mastriel.cutapi.item.ItemBuildersKt;
import xyz.mastriel.cutapi.registry.Identifier;
import xyz.mastriel.cutapi.registry.IdentifierKt;
import xyz.mastriel.cutapi.resources.ResourceRefKt;
import xyz.mastriel.cutapi.utils.ExtensionsKt;

public class Test {

    public Test() {
        CustomItem<?> item = ItemBuildersKt.customItem(
            IdentifierKt.id(BrazilPluginKt.getPlugin(), "test"),
            Material.STICK,
            (builder) -> {
                builder.display((display) -> {
                    Component text = ExtensionsKt.getColored("&6Example Item");
                    display.setName(text);
                    display.setTexture(ResourceRefKt.ref("example://items/example_item.png"));
                    return null;
                });
                return null;
            }
        );
    }
}

