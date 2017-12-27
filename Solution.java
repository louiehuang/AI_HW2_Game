import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * MiniMax with alpha-beta pruning
 */
public class Solution {
	public boolean boardPrint = false;
	
	Node initNode; //initial node from input file
	private int n; // The width and height of the square board (0 < n <= 26)
	private int p; // The number of fruit types (0 < p <= 9)
	private double remainingTime; // Remaining time in seconds

	int topK = 999;
	int count = 0; // count leaf node
	int cutoff = 3; //the bigger the cutoff is, the more accurate the max score is

	class Node {
		String action; //what action to take to get current board
		int score; // scores that player1 out-numbers player2
		char[][] board = new char[n][n]; //may use a position set

		public Node() {}
		public Node(char[][] board, String action, int score) {
			this.board = board;
			this.action = action;
			this.score = score;
		}
	}
	
	/**
	 * adjust depth according to remaining time and board
	 * What to consider:
	 * 1. remaining time, make sure not running out of time
	 * 2. whether it's at the beginning of the game (how many fruits have been eliminated, density)
	 * 3. n, p, actionSize
	 */
	public void adjustDepth(){
		HashMap<String, List<String>> actionMap = getActions(initNode);
		int actionSize = actionMap.size(); //get action numbers
		int positionSize = getPositionSet(initNode.board).size();
		float density = (float)actionSize / positionSize;
		
		/**1. set cutoff according to (1)n; (2)whether in first few steps; (3)density**/
		int diff = 20;
		if(density > 0.95)
			diff = (int) (n*n*0.01);
		else if(density > 0.9)
			diff = (int) (n*n*0.02);
		else
			diff = (int) (n*n*0.05);
		
		boolean inFirstSteps = (n*n - positionSize < diff);
		
		if(n <= 4) //[1,4]
			cutoff = 6; 
		else if(n <= 6) //[5,6]
			cutoff = 5;
		else if(n <= 10) //[7,10]
			cutoff = 4;
		else if(n <= 15){
			if(inFirstSteps)
				cutoff = 4;
		}else{ //(16,26]
			cutoff = 3;
		}
		
		/**2. consider remaining time, make sure it won't run out of time**/
		if(remainingTime < 10d){ //small board won't just remain 10 seconds, so this must be a large one
			cutoff = 1; //<5
			topK = 10;
		}else if(remainingTime < 70d && n < 20){ //[10,15)
			cutoff = Math.min(cutoff, 3);
			topK = 50;
		}else if(remainingTime < 100d && n < 20){ //[50,100) && n<20
			cutoff = Math.min(cutoff, 3);
		}else if(remainingTime < 15d && n >= 20){ 
			cutoff = Math.min(cutoff, 2);
			topK = 30;
		}else if(remainingTime < 50d && n >= 20){
			cutoff = Math.min(cutoff, 3);
			topK = 50;
		}else if(remainingTime < 100d && n >= 20){
			cutoff = Math.min(cutoff, 3);
			topK = 100;
		}else if(remainingTime < 200d && n >= 20){ //[100,200) && n >= 20,,  159, 26
			cutoff = Math.min(cutoff, 3);
			topK = 200;
		}
		
//		System.out.println("cutoff: " + cutoff + ", n: " + n + ", p: " + p + ", topK: " + topK + 
//				", actionSize: " + actionSize + ", positionSize: " + positionSize + ", density: " + density);
	}
	

	
	public Node alphaBetaSearch() {
		adjustDepth(); //depth
		
		//maxValue just return the best action, v.action
		//but node v is not what we need (v.board is not the status after applying v.action)
		Node v = maxValue(initNode, Integer.MIN_VALUE, Integer.MAX_VALUE, 0);
	
		if(v.action == null) //no valid action to take (empty board)
			return null;
		
		String[] pos = v.action.split(",");
		int row = Integer.parseInt(pos[0]), col = Integer.parseInt(pos[1]);
		List<String> fruitList = DFS(row, col, initNode.board, initNode.board[row][col], new HashSet<String>(),
				new ArrayList<String>());
		
		//total exceeding score (player1 to player2)
		if(boardPrint){
			System.out.println("Action to take: (" + v.action + ")");
			System.out.println("Max Exceeding Score:" + v.score + ", Leaf Node:" + count);
		}
		
		//player1
		Node res = applyAction(initNode, v.action, fruitList, true); //apply v.action to initial node
		
		if(boardPrint)
			printBoard(res);
		
		writeToFile(res); // board

		return v;
	}
	
	
	public Node maxValue(Node node, int alpha, int beta, int depth) {
		// cutoff
		HashMap<String, List<String>> actions = getActions(node); // get valid next positions
		if (actions.size() == 0 || depth >= cutoff) { // node.depth
			count++;
			return node;
		}

		Node v = pseudoDeepCopy(node);
		v.score = Integer.MIN_VALUE;
		
		//1. add to list
		List<Node> minNodeList = new ArrayList<>();
		
		for (Map.Entry<String, List<String>> entry : actions.entrySet()) {
			String action = entry.getKey();
			List<String> fruitList = entry.getValue();
			// true means it's player1's turn, score += point
			Node nextNode = applyAction(node, action, fruitList, true);
			minNodeList.add(nextNode);
		}
		
		//2. sort, In maxValue(), its children are minNode, sort them in descending order
		Collections.sort(minNodeList, new Comparator<Node>() {
		    @Override
		    public int compare(Node a, Node b) {
		        return b.score - a.score;
		    }
		});
		
		
		//top k
		int subLen = (minNodeList.size() < topK ? minNodeList.size() : topK);
		minNodeList = minNodeList.subList(0, subLen);
		
		
		//3. call minValue()
		for(Node nextNode : minNodeList){
			// v ← MAX(v, MIN-VALUE(RESULT(s,a), α, β))
			Node childNode = minValue(nextNode, alpha, beta, depth + 1);
			if (childNode.score > v.score) {
				v = pseudoDeepCopy(childNode);
				v.action = nextNode.action; // current action is better, change to it
			}

			if (v.score >= beta)
				return v; // return v;
			alpha = Math.max(alpha, v.score);
		}
		
		return v;
	}
	

	public Node minValue(Node node, int alpha, int beta, int depth) {
		// cutoff
		HashMap<String, List<String>> actions = getActions(node); // get valid next positions
		if (actions.size() == 0 || depth >= cutoff) { // eval(node);
			count++;
			return node;
		}

		Node v = pseudoDeepCopy(node);
		v.score = Integer.MAX_VALUE;
		
		//1. add to list
		List<Node> maxNodeList = new ArrayList<>();
		for (Map.Entry<String, List<String>> entry : actions.entrySet()) {
			String action = entry.getKey();
			List<String> fruitList = entry.getValue();
			// false means it's player2's  turn, score -= point
			Node nextNode = applyAction(node, action, fruitList, false);
			maxNodeList.add(nextNode);
		}
		

		//2. sort, In minValue(), its children are maxNode, sort them in ascending order
		Collections.sort(maxNodeList, new Comparator<Node>() {
		    @Override
		    public int compare(Node a, Node b) {
		        return a.score - b.score;
		    }
		});
		
		
		//top k
		int subLen = (maxNodeList.size() < topK ? maxNodeList.size() : topK);
		maxNodeList = maxNodeList.subList(0, subLen);
		
		
		//3. call maxValue()
		for(Node nextNode : maxNodeList){
			// v = Math.min(v, maxValue(nextNode, alpha, beta).score);
			Node childNode = maxValue(nextNode, alpha, beta, depth + 1);
			if (childNode.score < v.score) {
				v = pseudoDeepCopy(childNode);
				v.action = nextNode.action; // current action is better, change to it
			}

			if (v.score <= alpha)
				return v; // return v;
			beta = Math.min(beta, v.score);
		}

		return v;
	}

	
	/**
	 * Get all possible actions(positions to be chosen) for next move <br/>
	 * Each actions corresponds to a fruit set, For example: <br/>
	 * 0112<br/>
	 * 1102<br/>
	 * 0012<br/>
	 * 0022<br/>
	 * action (0,1) corresponds to set{(0,1),(0,2),(1,0),(1,1)} <br/>
	 * @param node
	 * @return
	 */
	public HashMap<String, List<String>> getActions(Node node) {
		HashMap<String, List<String>> actionMap = new HashMap<>();
		HashSet<String> exploredSet = new HashSet<>();
		for(int i = 0; i < n; i++){
			for(int j = 0; j < n; j++){
				String position = (i + "," + j);
				if(node.board[i][j] == '*' || exploredSet.contains(position))
					continue;
				List<String> fruitList = DFS(i, j, node.board, node.board[i][j], new HashSet<String>(),
						new ArrayList<String>());
				if(fruitList != null){
					actionMap.put(position, fruitList); // String position -> Set positions
					exploredSet.addAll(fruitList);
				}
			}
		}
		return actionMap;
	}

	/**
	 * @param node
	 * @param action a set of positions, means pick fruit at that position
	 * @param player1 it's player1's turn
	 * @return board after picking position 'action'
	 */
	public Node applyAction(Node node, String action, List<String> fruitList, boolean player1) {
		char[][] boardCopy = deepCopyBoard(node.board);
		int gainScore = fruitList.size() * fruitList.size(); 

		// Eliminate fruits
		for (String fruit : fruitList) {
	        StringTokenizer token = new StringTokenizer(fruit, ",");
	        int row = Integer.parseInt(token.nextToken()); 
	        int col = Integer.parseInt(token.nextToken()); 
			boardCopy[row][col] = '*';
		}

		/**
		 * calculate scores, since there are just 2 players, 
		 * we can use a single variable 'score' to record how many points player1 out-numbers player2
		 * if score > 0 at the end of game, then play1 wins
		 * so when it's player1's turn, score += points; 
		 * when it's player2's turn, score -= points;
		 */
		int newScore = node.score;
		if (player1)
			newScore += gainScore;
		else
			newScore -= gainScore;

		/**
		 * apply gravity to the new board
		 */
		applyGravity(boardCopy); // new board

		return new Node(boardCopy, action, newScore);
	}

	
	/**
	 * Gravity, sink fruits
	 * @param node
	 */
	public void applyGravity(char[][] board) {
		for (int j = 0; j < n; j++) { // for each column, left to right
			int i = n - 1;
			while (i >= 1) {
				while (i >= 0 && board[i][j] != '*') { // find a '*'
					i--;
				}
				int t = i - 1;
				while (t >= 0 && board[t][j] == '*') { // find a fruit (not '*')
					t--;
				}

				// swap
				if (i >= 0 && t >= 0) {
					char tmp = board[i][j];
					board[i][j] = board[t][j];
					board[t][j] = tmp;
				} else { // t < 0
					break;
				}
			}
		}
	}

	/**
	 * get valid positions, will be used to get valid actions
	 * @param board
	 * @return
	 */
	public HashSet<String> getPositionSet(char[][] board) {
		HashSet<String> positionSet = new HashSet<>();
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (board[i][j] != '*') {
					positionSet.add(i + "," + j);
				}
			}
		}
		return positionSet;
	}

	
	/**
	 * use DFS to find same type fruit, 4 directions
	 * @param p
	 * @param board
	 * @param fruitType
	 * @param exploredSet
	 * @return position set(fruits of same type), so to calculate points
	 */	
	public List<String> DFS(int row, int col, char[][] board, char fruitType, HashSet<String> exploredSet,
			List<String> fruitList) {
		if (row < 0 || row >= n || col < 0 || col >= n)
			return null;

		String p = (row + "," + col);
		if(board[row][col] == '*' || exploredSet.contains(p) || board[row][col] != fruitType)
			return null;
		
		exploredSet.add(p);
		fruitList.add(p);
		
		DFS(row-1, col, board, fruitType, exploredSet, fruitList); // Up
		DFS(row+1, col, board, fruitType, exploredSet, fruitList); // Down
		DFS(row, col-1, board, fruitType, exploredSet, fruitList); // Left
		DFS(row, col+1, board, fruitType, exploredSet, fruitList); // Right
		
		return fruitList;
	}
	
	
	public Solution() {}

	public Solution(String inputFilePath) {
		initNode = readFile(inputFilePath);
	}

	/**
	 * pseudo deep copy, just copy action and score
	 * @param oriNode
	 * @return
	 */
	public Node pseudoDeepCopy(Node oriNode) {
		//no need to copy board
		//what I need are just action and score
		int newScore = oriNode.score;
		String newAction = (oriNode.action == null ? null : new String(oriNode.action));
		return new Node(oriNode.board, newAction, newScore);
	}
	
	public char[][] deepCopyBoard(char[][] oriBoard) {
		char[][] boardCopy = new char[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++)
				boardCopy[i][j] = oriBoard[i][j];
		}
		return boardCopy;
	}

	public HashSet<String> deepCopySet(HashSet<String> oriSet) {
		HashSet<String> setCopy = new HashSet<>();
		// setCopy.addAll(oriSet);
		for (String str : oriSet) {
			setCopy.add(str);
		}
		return setCopy;
	}

	public void printBoard(Node node) {
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++)
				System.out.print(node.board[i][j]);
			System.out.println();
		}
	}
	
	/**
	 * Parse input file
	 * @param filePath
	 * @return initNode
	 */
	public Node readFile(String filePath) {
		int count = 0;
		Node initNode = null;
		try {
			File file = new File(filePath);
			if (file.isFile() && file.exists()) {
				InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(file), "GBK"); //
				BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
				String lineContent = null;
				while ((lineContent = bufferedReader.readLine()) != null) {
					if (count == 0) {
						this.n = Integer.parseInt(lineContent.trim());
						initNode = new Node();
					} else if (count == 1) {
						this.p = Integer.parseInt(lineContent.trim());
					} else if (count == 2) {
						this.remainingTime = Double.parseDouble(lineContent.trim());
					} else { // count >= 3
						for (int j = 0; j < lineContent.length(); j++) {
							initNode.board[count - 3][j] = lineContent.charAt(j);
						}
					}
					count++; // next line
				}
				inputStreamReader.close();
			} else {
				System.out.println("cannot find file");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return initNode;
	}
	
	public void writeToFile(Node node){
		String content = "";
		String[] pos = node.action.split(",");
		Integer row = Integer.parseInt(pos[0]) + 1; //index starts from 0, so plus 1
		char col = (char) (Integer.parseInt(pos[1]) + 'A'); //no need to plus 1
		
		try {
			content += (col + row.toString() + "\r\n");
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++)
					content += node.board[i][j];
				content += i == (n - 1) ? "" : "\r\n";
			}
            File file = new File("output.txt");
            if (!file.exists()) {
                file.createNewFile();
            }
            BufferedWriter bw = new BufferedWriter(new FileWriter(file.getAbsoluteFile()));
            bw.write(content);
            bw.close();
        }catch( IOException e) {
        	e.printStackTrace();
        }
	}
}