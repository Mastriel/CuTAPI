package xyz.mastriel.cutapi.block




sealed class CuTPlacedTile {
}


open class CuTPlacedTileEntity : CuTPlacedTile() {

}

open class CuTPlacedBlock : CuTPlacedTile() {

}