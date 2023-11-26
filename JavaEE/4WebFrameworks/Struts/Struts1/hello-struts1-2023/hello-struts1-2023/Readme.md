1) Hello struts1 example but with some tricky thing:
   In HelloWordForm there is not just plain properties (String), but there is list akaList
   THIS list is displayed on jsp page, and aldo THERE IS POSSIBILITY to add new elements to this list (by pressing button)
   !!!!! THIS is the tricky (sizing of the list) part:
I must modify getter method to sync size from HTTP request and STRUTS form:
   public List<HelloWorldAction.MyEntry> getAkaList() {
   if(this.akaListSize > this.akaList.size()){
   int diff = this.akaListSize - this.akaList.size();
   for (int i = 0; i < diff; i++) {
   akaList.add(new HelloWorldAction.MyEntry());
   }
   }
   return akaList;
   }
