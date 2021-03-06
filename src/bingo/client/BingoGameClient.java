package bingo.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;

import bingo.client.ui.*;
import bingo.data.Data;
import bingo.data.GameInfo;
import bingo.data.GameRoom;
import bingo.data.User;
import bingo.server.BingoGameServer;
import bingo.server.countThread;

/**
 * @author user
 *
 */
public class BingoGameClient extends JFrame implements Runnable {

	private ObjectOutputStream oos;
	private ObjectInputStream ois;
	// private LoginUI login;
	private Socket socket;
	public Data data;// 서버로부터 돌아오는 데이터, 이걸 GUI에 반영해야함, 클라객체에서
	private User now_user;
	private GameLobbyUI ui2;
	private GameRoomUI roomUI;
	private HashMap<String, GameRoom> roomList;
	private Data shareData;
	private String makeHost_id;
	private boolean isIn;
	private HashMap<String, User> hm;

	public static void main(String[] args) {
		// new LoginUI();//GUI에서 로그인을 해야지 소켓이 생성되게 하려면 시작점이 LOGIN GUI
		new LoginUI();

	}

	/**
	 * 생성자의 역할 소켓생성
	 */
	public BingoGameClient() {

		try {
			socket = new Socket("localhost", 7777);
			oos = new ObjectOutputStream(socket.getOutputStream());
			ois = new ObjectInputStream(socket.getInputStream());

			Thread t = new Thread(this);
			t.start();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, "서버를 켜주세요");// GUI에서 하는게 더 나을 듯
			// e.printStackTrace();
			System.exit(-1);

		}
	}

	/**
	 * 리스너스레드역할 서버의 응답을 받는다.
	 */
	@Override
	public void run() {

		boolean exit = false;
		while (!exit) {
			try {
				data = (Data) ois.readObject();// 서버스레드로부터 응답 받음
				switch (data.getCommand()) {
				case Data.LOGIN: // LoginUI 입장,로그인 버튼 이벤트에서
					this.loginExe();
					break;
				case Data.EXIT: // 로비에서 종료 시 접속자 갱신

					this.exitExe();

					break;
				case Data.MAKE_ROOM:
					// makeRoom에서 방 만들 때
					this.makeRoomExe();
					break;
				case Data.JOIN:
					// 게임방참여
					this.joinExe();
					break;
				case Data.OUT:
					// 게임룸에서 나갈때 목록 지워야지않아?
					/*
					 * 딱히 해줄 것 있나? 테이블 GUI삭제는 언제해줘야할까? 싹다 삭제하고 다시 생성하는게 좋을거 같은데
					 * 또 다른 누군가 게임방 참여할때? 그러면 참여전까지는 갱신이 안되는데 근데 갱신할 필요가 없긴한듯
					 */
					data.getRoomList();// make 시 갱신하는 방목록
					data.getGameRoom();// 나가기 이벤트가 일어난 방

					break;
				case Data.CHAT_MESSAGE:// 게임방에서 채팅 시
					this.chatExe();
					break;
				case Data.GAME_READY:// 게임방에서 게임 준비버튼 눌러서 서버에서 처리한 결과
					this.readyExe();
					break;
				case Data.GAME_START:// 게임시작
					this.startExe();
					break;
				case Data.SEND_BINGO_DATA:// 게임진행결과
					break;
				case Data.SEND_WINNING_RESULT:// 게임승패
					break;

				default:
					break;
				}

			} catch (ClassNotFoundException | IOException e) {
				exit = true;
				System.out.println("서버가 종료되었습니다.");
			}
		}
	}

	/**
	 * 로그인 시 실행되는 리스너
	 */
	private void loginExe() {
		now_user = data.getUser();// 현접자
		// 전체리스트도 가져옴(로그인GUI에서 접속시마다 추가됨)
		ArrayList<User> RoomuserList = data.getUserList();// 여기서
		GameLobbyUI.getInstance();

		// 접속자 목록 추가
		GameLobbyUI.dlm.removeAllElements();// 누구 들어올 때마다 싹지운다
		for (User user : RoomuserList) {// 리스트 돌려서 재출력
			GameLobbyUI.dlm.addElement(user);
		}

		// 로비 테이블 방목록 추가
		roomList = data.getRoomList();
		GameLobbyUI.tm.setRowCount(0);
		for (Entry<String, GameRoom> entry : roomList.entrySet()) {
			String hostIndex = entry.getKey();
			GameRoom room = entry.getValue();
			String row[] = { room.getRoomID(), room.getTitle(), room.getTheme(),
					Integer.toString(room.getMaxUserNum()) };
			GameLobbyUI.tm.addRow(row);
			GameLobbyUI.tableCellAlign();
		}
		GameLobbyUI.lblNewLabel_2.setText("접속인원 :" + RoomuserList.size() + " 명");

	}

	/**
	 * 나가기 시 실행되는 리스너
	 */
	private void exitExe() {
		User exit_user = data.getUser();// 현재접속자정보(본인)
		GameLobbyUI.dlm.removeElement(exit_user);// 리스트에서 빼준다
		ArrayList<User> new_userList = data.getUserList();

		GameLobbyUI.dlm.removeAllElements();// 누구 들어올 때마다 싹지운다
		for (User user : new_userList) {// 리스트 돌려서 재출력
			GameLobbyUI.dlm.addElement(user);
		}
	}

	/**
	 * 방만들기 실행되는 리스너 방을 만든 사람과 로비에 있는 사람의 실행내용 분기처리
	 * 
	 * //System.out.println("받아온 방장 아이디" + data.getUser());
	 * //System.out.println("받아온 방장의 권한" + // data.getUser().getPrivilege());
	 * //System.out.println("지금 나는 누구인가 : " +
	 * GameLobbyUI.getInstance().getUser());
	 */
	private void makeRoomExe() {
		shareData = data;
		GameRoom gameRoom = data.getGameRoom();
		HashMap<String, GameRoom> roomList = data.getRoomList();
		User host = GameLobbyUI.getInstance().getUser();

		// 방장 //TODO 룸테이블추가 //TODO 룸라벨 갱신 //TODO 로비감추기
		if (host.getId().equals(data.getUser().getId())) {
			System.out.println("방만들기명령+방장만 실행");
			String id = host.getId();// 로그인 UI에서
			String state = host.getState();// 로비 GUI에서
			String[] rowData = { "-", id, state, "-" };
			GameRoomUI.tm.addRow(rowData);
			GameRoomUI.tableCellAlign();
			// 입장한 방에 라벨갱신
			GameRoomUI.getInstance().setGameTitle("[방제목 : " + gameRoom.getTitle() + "]");
			GameRoomUI.getInstance().setGameUser("[방장 : " + data.getUser().getId() + "]");
			GameRoomUI.getInstance().setData(shareData);
			// 로비감추기
			GameLobbyUI.getInstance().frame.setVisible(false);
		}

		// 전체 //TODO 로비테이블추가
		GameLobbyUI.tm.setRowCount(0);
		for (Entry<String, GameRoom> entry : roomList.entrySet()) {
			String hostIndex = entry.getKey();
			GameRoom room = entry.getValue();
			String row[] = { room.getRoomID(), room.getTitle(), room.getTheme(),
					Integer.toString(room.getMaxUserNum()) };
			GameLobbyUI.tm.addRow(row);
			GameLobbyUI.tableCellAlign();
		}

		makeHost_id = data.getUser().getId();// join 시 쓰기 위함

	}

	/**
	 * 조인 시 실행되는 리스너 // System.out.println("1번 아이디 " + now_user_id);// 나중에 접속한
	 * 녀석 // System.out.println("2번 아이디 " + host_id);// 방장 String now_user_id =
	 * GameLobbyUI.getInstance().getUser().getId();//현사용자
	 */
	private void joinExe() {
		GameRoom joinRoom = data.getGameRoom();// 나 혹은 누군가가 참여하려는 방
		User joiner = data.getUser();// 실제 방에 조인한 사람
		User now_user = GameLobbyUI.getInstance().getUser();// 현사용자
		isIn = false;
		String myRoomID = "";
		String host_id = joinRoom.getHostID();
		HashMap<String, User> userMap = data.getGameRoom().getUserList();
		boolean check = joiner.getId().equals(now_user.getId());// 조인신청자 확인 변수

		System.out.println("조인룸 아이디 " + joinRoom.getRoomID());

		try {
			myRoomID = now_user.getRoom().getRoomID();
		} catch (Exception e) {
			System.out.println("로비있는 애는 널이지만 무시한다.");
		}
		
		if (now_user.getRoom().getRoomID() != null) {
			// 로비에 있는 애는 당연히 null,
		}

		System.out.println("마이 룸 아이디 " + myRoomID);
		boolean check2 = joinRoom.getRoomID().equals(myRoomID);

		// TODO 방장인 경우
		if (host_id.equals(now_user.getId())) {
			// 방장의 게임테이블
			System.out.println("방장 : 조인갱신");
			now_user.setRoom(joinRoom);
			// isIn = true;
		}

		// 조인 신청자만 보임, 내가 들어간 방 아이디 vs 조인이벤트 발생한 방 아이디 구분
		if (check && check2) {
			GameRoomUI.getInstance().setData(shareData);
			GameRoomUI.getInstance().setGameTitle("[방제목 : " + joinRoom.getTitle() + "]");
			GameRoomUI.getInstance().setGameUser("[아이디 : " + joiner.getId() + "]");
			GameRoomUI.getInstance();// 조인한 사람만 게임창보이기

			// 조이너의 게임창 테이블 갱신
			System.out.println("조이너 : 조인갱신");
			GameLobbyUI.getInstance().frame.setVisible(false);// 조인 시 로비 감추도록
			// 다음 조인할 때 3번째 분기처리 플래그변수
			isIn = false;
			joinRoom.setHostID(host_id);// 방장정보 저장

			now_user.setRoom(joinRoom);// 현접자에 현재방저장
			this.tableRenew(userMap);// 이걸 조건문 밖으로 빼면 다른 방있는 애들도 여기 hm으로 업뎃됨 조심!

		}

		// 뉴조이너는 실행안하게 하고 , 기존 조이너가 해당 방의 정보를 가지고 있는 경우만 (조인할 때 추가해줘야함)
		// 그지같은 조건... 어쩔 수 없이 방에 들어와 있으면 무조건 실행하도록 함
		// 근데 이렇게 하니까 방2개 먼저 만들고 들어오면 두번째 방 테이블이 잘못되는 문제
		if (joinRoom.getHostID().equals(now_user.getRoom().getHostID())) {
			System.out.println("멤버 : 조인갱신");
			this.tableRenew(userMap);
		}
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////
	private void chatExe() {
		String name = data.getUser().getId();// 챗한 사람 아이디

		String eventRoomID = data.getGameRoom().getRoomID();// TODO 채팅이 발생한 방의
															// 아이디
		String myRoomID = data.getUser().getRoom().getRoomID();// 내가 들어있는 방의 아이디
																// , 로비에 있어서
																// null인 경우는?

		if (myRoomID == null) {// 방에 일단 들어온 사람 중

			if (myRoomID.equals(eventRoomID)) { // 같은 방 있는 경우만. 방의 id로 검색하자
				String str = data.getMessage();
				String msg = String.format("[%s]  %s%n", name, str);
				GameRoomUI.getInstance().getTextArea_1().append(msg);
				GameRoomUI.getInstance().getTextArea_1()
						.setCaretPosition(GameRoomUI.getInstance().getTextArea_1().getDocument().getLength());
			}
		}

	}

	private void readyExe() {

		GameRoom stRoom = data.getGameRoom();
		// JOptionPane.showMessageDialog(this, stRoom);// 룸정보, 잘 나옴
		User now_user = data.getUser();// 현재사용자
		String state = data.getUser().getState();// 상태(준비)
		GameInfo info = data.getGameInfo();// 게임정보{현사용자,버튼의 텍스트 2차원 배열정보, 이미들어있음
											// }
		String[][] bingoKeywords = info.getBingoKeyword();

		User gameHost = data.getUser().getRoom().getGamehost();// 레디누른 사람
		String readyRoomId = gameHost.getRoom().getRoomID();// 레디를 한 사람의 방 id
		String myRoomID = now_user.getRoom().getRoomID();// 현재 내가 들어간 방id , 안들가면
															// null일 수 있음

		// String hostID = data.getUser().getRoom().getHostID();
		System.out.println("레디한 사람의 방 id : " + readyRoomId);
		System.out.println("내가 들어간 방 : " + myRoomID);

		// 내가 들어간 방과 게임호스트의 방이 같은가
		if (state.equals(User.READY) && readyRoomId.equals(myRoomID)) {
			hm = stRoom.getUserList();// a
			now_user.setState(User.DONE);
			hm.put(now_user.getId(), now_user);
			// 테이블에 준비상태로 바꾸기
			JOptionPane.showMessageDialog(null, "준비완료!");
			this.tableRenew(hm);// 준비완료로 갱신
			// 게임시작되면 턴인 애한테 줘야할듯한데
			countThread ct = new countThread();
			Thread t = new Thread(ct);
			t.start();// 카운트 시작

			// 상태바꾸기

		} else if (!state.equals(User.READY) == readyRoomId.equals(myRoomID)) {
			// 같은 방에 있고 레디안한 녀석들
			hm.put(now_user.getId(), now_user);
			JOptionPane.showMessageDialog(this, "다른 사람이 레디했습니다! 너도 좀 하세요");
			this.tableRenew(hm);// 준비완료로 갱신
		} else if (!state.equals(User.READY) == !readyRoomId.equals(myRoomID)) {
			// 룸번호가 없고 준비상태가 아닌 녀석들 , 즉 밖에 있는 녀석들

		}

	}

	private void startExe() {
		// TODO Auto-generated method stub
		// String Keywords = info.getKeyword();//방금 누른 키(이건 게임 시작 이후에나 필요할듯)

	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * 테이블 새로고침
	 * 
	 * @param userMap
	 */
	private void tableRenew(HashMap<String, User> userMap) {
		GameRoomUI.tm.setRowCount(0);
		for (Entry<String, User> entry : userMap.entrySet()) {
			String key = entry.getKey(); // 유저 아이디
			User user = entry.getValue();
			String row[] = { "-", user.getId(), user.getState(), "-" };
			GameRoomUI.tm.addRow(row);
			GameRoomUI.tableCellAlign();
		}
	}

	/**
	 * 블럭화 GUI에서 데이터를 보내줄 때 사용한다.
	 * 
	 * @param data
	 */
	public void sendData(Data data) {
		try {
			oos.writeObject(data);
			oos.reset();// 리셋은 보내고선 해야함
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public ObjectOutputStream getOos() {
		return oos;
	}

	public void setOos(ObjectOutputStream oos) {
		this.oos = oos;
	}

	public ObjectInputStream getOis() {
		return ois;
	}

	public void setOis(ObjectInputStream ois) {
		this.ois = ois;
	}

}
