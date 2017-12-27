
public class homework {
	
	public static void main(String[] args) {
		long start = System.currentTimeMillis();
		//in vocareum, inputFilePath must be "input.txt"
		
		String inputFilePath = "input.txt";
		
		//get input file path from args
		if(args.length != 0)
			inputFilePath = args[0];
		
		Solution solution = new Solution(inputFilePath);
//		solution.boardPrint = true;
		
		solution.alphaBetaSearch();
		
		long end = System.currentTimeMillis();
		System.out.println((end-start) + "ms");	
	}
}
