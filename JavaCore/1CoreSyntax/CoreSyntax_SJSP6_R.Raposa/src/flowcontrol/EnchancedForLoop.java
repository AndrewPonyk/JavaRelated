package flowcontrol;

import java.util.ArrayList;

public class EnchancedForLoop {
	public static void main(String[] args) {
		ArrayList<Integer> list = new ArrayList<>();
		
		list.add(10);
		list.add(100);
		list.add(1000);
		list.add(10000);
		
		for(Integer item : list){
			System.out.println(item);
		}
		System.out.println("--------");
		for(int i=0; i<list.size();i++){
			if(list.get(i) == 10 || list.get(i) == 1000){
				list.remove(i);
			}
		}
		
		for(Integer item : list){
			System.out.println(item);
		}
		
	}
}
