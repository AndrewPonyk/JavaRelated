package models;

import java.util.TreeMap;

public interface MaterialCatalogInterface {

	public void addMaterial(Material newMaterial);
	public TreeMap<String, Material> getMaterialMap();
	public Material findMaterial(String title) throws MaterialNotFoundException;
	public int getNumberOfMaterials();
	
}
