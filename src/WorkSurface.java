import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDesktopPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;

public class WorkSurface {

	final Logger logger = Logger.getLogger(this.getClass().getName());
	
	JFrame mainFrame;
	JDesktopPane desktop;
	JToolBar toolbar;
	
	DefaultComboBoxModel<String> scenes;
	DefaultComboBoxModel<String> stories;
	
	Map<String, SceneView> sceneViews;
	Map<String, StoryView> storyViews;
	
	Map<Integer, CameraView> cameraViews;
	
	File workingFolder = null;
	
	//to do, add prompt on exit if modified
	boolean unsaved = false;
	
	/**
	 * Simple panel to display an image
	 * @author antho
	 *
	 */
	class ImagePanel extends JPanel {

		private Image image = null;
		
		public void setImage(Image img) {
			ImagePanel.this.image = img;
			ImagePanel.this.repaint();
		}
		
		public Image getImage() {
			return this.image;
		}
		
		public Image cloneImage() {
			if(image == null)return null;
			BufferedImage copyOfImage = 
					   new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
					Graphics g = copyOfImage.createGraphics();
					g.drawImage(image, 0, 0, null);
					g.dispose();
			return copyOfImage;
		}
		
		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			if(image == null)return;
			Dimension d = this.getSize();
			float h = image.getHeight(null);
			float w = image.getWidth(null);
			float ratio = h/w;
			g.drawImage(image, 0, 0, d.width, (int) (((float) d.width)*ratio), null);//, arg3, arg4, arg5, arg6, arg7, arg8, arg9)
		}
		
	}
	
	/**
	 * Class listens for a connected "camera" sending png images over the supplied socket, allows capturing 
	 * the current image to a scene
	 * @author antho
	 *
	 */
	class CameraView extends JInternalFrame {
		
		ImagePanel imagePanel;
		int port;
		JComboBox sceneSelector;
		
		public CameraView(int port) {
			super("Camera: " + port, true, false, true, true);
			this.sceneSelector = new JComboBox(scenes);
			this.port = port;
			this.imagePanel = new ImagePanel();
			this.getContentPane().add("Center", imagePanel);
			JButton capture = new JButton("Capture");
			capture.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					String scene = sceneSelector.getSelectedItem().toString();
					int cursor = sceneViews.get(scene).getCursorPosition();
					sceneViews.get(scene).addImage(cursor, imagePanel.cloneImage());
				}
			});
			JPanel control = new JPanel(new FlowLayout(FlowLayout.CENTER));
			control.add(sceneSelector);
			control.add(capture);
			this.getContentPane().add("South", control);
			this.setSize(new Dimension(600, 400));
			startMonitor();
			setVisible(true);
		}
		
		public void startMonitor() {
			Runnable r = new Runnable() {
				@Override
				public void run() {
					while(true) {
				        ServerSocket serverSocket = null;
				    	
				        try {
				            serverSocket = new ServerSocket(port);
				        } catch (IOException ex) {
				            System.out.println("Can't setup server on this port number. ");
				        }
				
				        Socket socket = null;
				        InputStream in = null;
				
				        try {
				            socket = serverSocket.accept();
				        } catch (IOException ex) {
				            System.out.println("Can't accept client connection. ");
				        }
				
				        try {
				            in = socket.getInputStream();
				        } catch (IOException ex) {
				            System.out.println("Can't get socket input stream. ");
				        }
				        
				        
						try {
							BufferedImage image = ImageIO.read(in);//PNGDecoder.decode(in);
							in.close();
							socket.close();
							serverSocket.close();
							SwingUtilities.invokeLater(new Runnable() {
								@Override
								public void run() {
									imagePanel.setImage(image);
								}
							});
						} catch (IOException e2) {
							logger.info(e2.getMessage());
						}
					}
				}
				
			};
			new Thread(r).start();
		}

	}
	
	/**
	 * Simple renderer for showing thumbnails in components, uses a hashmap to cache thumbs in use
	 * @author antho
	 *
	 */
	public class ImageRenderer extends DefaultListCellRenderer {
		 
		Map<Image, ImageIcon> thumbHash = new HashMap<Image, ImageIcon>();
		
		public ImageRenderer() {
			super();
		}
		
	    @Override
	    public Component getListCellRendererComponent(JList list, Object object, int index,
	        boolean isSelected, boolean cellHasFocus) {
	        
	    	if(object == null)return null;
	    	
	    	Component c = super.getListCellRendererComponent(list, object, index, isSelected, cellHasFocus);
	    	
	    	Image img = null;
	    	
	    	if(object instanceof String) {
	    		if(sceneViews.containsKey(object)) {
		    		if(!sceneViews.get(object).listModel.isEmpty()) {
		    			img = sceneViews.get(object).listModel.firstElement();
		    		}
	    		}
	    	}else if(object instanceof Image) {
	    		img = (Image) object;
	    	}
	    	
	    	if(img == null)return c;
	    	
	    	JLabel l = (JLabel) c;
	    	ImageIcon imageIcon = thumbHash.get(img);
	    	if(imageIcon == null) {
	    		Image ii = img;
	    		float ratio = ((float) ii.getWidth(null)) / ((float) ii.getHeight(null));
	    		Image imageClone = ii.getScaledInstance(128, (int) (128.0f/ratio), Image.SCALE_SMOOTH);
	    		imageIcon = new ImageIcon(imageClone);
	    		thumbHash.put(ii, imageIcon);
	    	}
	         
	        l.setIcon(imageIcon);
	        l.setText("" + index);
	        l. setVerticalTextPosition(JLabel.BOTTOM);
	        l.setHorizontalTextPosition(JLabel.CENTER);
	         
	        return l;
	    }
	     
	}
	
	/**
	 * A named collection of images
	 * @author antho
	 *
	 */
	class SceneView extends JInternalFrame {
		
		ImagePanel currentFrame;
		JLabel currentFrameLabel;
		JList<Image> frames;
		DefaultListModel<Image> listModel;
		JComboBox<String> storySelector;
		
		public SceneView(String name) {
			super(name, true, false, true, true);
			super.setName(name);
			currentFrameLabel = new JLabel("Frame");
			currentFrame = new ImagePanel();
			currentFrame.setPreferredSize(new Dimension(500,300));
			listModel = new DefaultListModel<Image>();
			
			storySelector = new JComboBox<String>(stories);
			frames = new JList<Image>(listModel);
			frames.setCellRenderer(new ImageRenderer());
			frames.setLayoutOrientation(JList.HORIZONTAL_WRAP);
			frames.setVisibleRowCount(-1);
			frames.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			frames.addListSelectionListener(new ListSelectionListener() {
				@Override
				public void valueChanged(ListSelectionEvent arg0) {
					currentFrame.setImage(frames.getSelectedValue());
					currentFrameLabel.setText("Frame " + frames.getSelectedIndex());
				}
			});
			
			JButton moveLeft = new JButton("<<--");
			moveLeft.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					if(frames.getSelectedIndex() <= 0)return;
					int sel = frames.getSelectedIndex();
					//remove the item to the left
					Image removed = listModel.remove(sel-1);
					listModel.add(sel, removed);
				}
			});
			
			JButton moveRight = new JButton("-->>");
			moveRight.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					if(frames.getSelectedIndex() >= listModel.size()-1)return;
					if(frames.getSelectedIndex() < 0)return;
					int sel = frames.getSelectedIndex();
					//remove the item to the right
					Image removed = listModel.remove(sel+1);
					listModel.add(sel, removed);
				}
			});
			
			JButton delete = new JButton("Delete");
			delete.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					if(frames.getSelectedIndex() >= listModel.size())return;
					if(frames.getSelectedIndex() < 0)return;
					int sel = frames.getSelectedIndex();
					listModel.remove(sel);
				}
			});
			
			JButton copy = new JButton("Duplicate");
			copy.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					if(frames.getSelectedValue() == null)return;
					if(currentFrame.getImage() == null)return;
					Image cln = currentFrame.cloneImage();
					int sel = frames.getSelectedIndex();
					//remove the item to the right
					listModel.add(sel, cln);
				}
			});
			
			JButton rename = new JButton("Rename");
			rename.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					String name = SceneView.this.getName();
					String newName = JOptionPane.showInputDialog((Component) ae.getSource(), "Name:", name);
					if(newName != null) {
						if(sceneViews.containsKey(newName)) {
							JOptionPane.showMessageDialog((Component) ae.getSource(), "Name already in use");
						}else {
							SceneView.this.rename(newName);
						}
					}
				}
			});
			
			//to do, make setting
			int frameRate = 100;
			JButton play = new JButton("Play");
			play.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					Runnable r = new Runnable() {
						@Override
						public void run() {
							int startIndex = frames.getSelectedIndex();
							if(startIndex < 0)startIndex = 0;
							for(int i = startIndex; i < listModel.getSize() + 1; i++) {
								final int c = i == listModel.getSize() ? startIndex : i;
								SwingUtilities.invokeLater(new Runnable() {
									@Override
									public void run() {
										frames.setSelectedIndex(c);
									}
								});
								try {
									Thread.sleep(frameRate);
								} catch (InterruptedException e) {}
							}
						}
					};
					new Thread(r).start();
				}
			});
			
			JPanel control = new JPanel(new FlowLayout(FlowLayout.CENTER));
			control.add(moveLeft);
			control.add(play);
			control.add(copy);
			control.add(rename);
			control.add(delete);
			control.add(moveRight);
			
			JButton capture = new JButton("Add to Story");
			capture.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					String story = storySelector.getSelectedItem().toString();
					if(storyViews.containsKey(story)) {
						int cursor = storyViews.get(story).getCursorPosition();
						storyViews.get(story).addScene(cursor, SceneView.this.getName());
					}
				}
			});
			JPanel capturePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
			capturePanel.add(storySelector);
			capturePanel.add(capture);
			JPanel center = new JPanel(new BorderLayout());
			center.add("Center", currentFrame);
			center.add("South", capturePanel);
			JPanel main = new JPanel(new BorderLayout());
			main.add("Center", center);
			main.add("South", control);
			main.add("North", currentFrameLabel);
			SceneView.this.getContentPane().add("Center", main);
			JScrollPane sp = new JScrollPane(frames);

			SceneView.this.getContentPane().add("South", sp);
			
			SceneView.this.setTitle(name);
		}
		
		public void rename(String newName) {
			String currentName = super.getName();
			SceneView.this.setName(newName);
			SceneView.this.setTitle(newName);
			sceneViews.put(newName, this);
			for(StoryView view : storyViews.values()) {
				for(int i = 0; i < view.listModel.getSize(); i++) {
					if(view.listModel.getElementAt(i).contentEquals(currentName)) {
						view.listModel.setElementAt(newName, i);
					}
				}
			}
			sceneViews.remove(currentName);
			int currentIndex = scenes.getIndexOf(currentName);
			scenes.removeElement(currentName);
			scenes.insertElementAt(newName, currentIndex);
		}
		
		public int getCursorPosition() {
			return frames.getSelectedIndex();
		}
		
		public void addImage(int index, Image image) {
			if(image == null)return;
			if(index < 0 || index > listModel.getSize()) {
				index = listModel.getSize();
			}
			listModel.add(index, image);
			currentFrame.setImage(image);
			pack();
			repaint();
		}
		
		public void store(String key, Properties properties) {
			String dirName = sanitizeString(key);
			File dir = new File(workingFolder, dirName);
			if(!dir.exists()) {
				if(!dir.mkdirs()) {
					logger.warning("Unable to create scene directory: " + dir.getAbsolutePath());
				}
			}
			Vector<String> names = new Vector<String>();
			for(int index = 0; index < listModel.getSize(); index++) {
				String name = "frame_" + index + ".png";
				WorkSurface.this.storeImage(new File(dir, name), listModel.elementAt(index));
				names.add(name);
			}
			properties.setProperty(key, String.join(",", names));
		}
		
		public void restore(String s, Properties properties) {
			String dirName = sanitizeString(s);
			File dir = new File(workingFolder, dirName);
			if(!dir.exists()) {
				logger.warning(dir.getAbsolutePath() + " does not exist");
				return;
			}
			String imageList = properties.getProperty(s, null);
			if(imageList != null) {
				String[] fileNames = imageList.split(",");
				for(String fn : fileNames) {
					File f = new File(dir, fn);
					BufferedImage image = null;
					try {
						image = ImageIO.read(f);
					} catch (IOException e) {
						logger.warning("Problem reading " + f.getAbsolutePath() + ": " + e.getMessage());
					}
					if(image != null) {
						SceneView.this.addImage(-1, image);
					}
				}
			}
		}
		
	}
	
	/**
	 * A sequence of scenes defines a story.  Scenes can be used more than once.
	 * @author antho
	 *
	 */
	class StoryView extends JInternalFrame {
		
		ImagePanel currentFrame;
		JLabel currentFrameLabel;
		JList<String> sceneList;
		String storyName;
		DefaultListModel<String> listModel;
		
		public StoryView(String name) {
			super(name, true, false, true, true);
			super.setName(name);
			currentFrameLabel = new JLabel("Frame");
			this.storyName = name;
			currentFrame = new ImagePanel();
			currentFrame.setPreferredSize(new Dimension(500,300));
			listModel = new DefaultListModel<String>();
			sceneList = new JList<String>(listModel);
			sceneList.setCellRenderer(new ImageRenderer());
			sceneList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
			sceneList.setVisibleRowCount(-1);
			sceneList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			sceneList.addListSelectionListener(new ListSelectionListener() {
				@Override
				public void valueChanged(ListSelectionEvent arg0) {
					String scene = sceneList.getSelectedValue();
					if(scene == null)return;
					if(!sceneViews.containsKey(scene))return;
					SceneView sv = sceneViews.get(scene);
					Image img = sv.listModel.firstElement();
					if(img == null)return;
					currentFrame.setImage(img);
					currentFrameLabel.setText(scene + " frame 0");
				}
			});
			
			JButton moveLeft = new JButton("<<--");
			moveLeft.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					if(sceneList.getSelectedIndex() <= 0)return;
					int sel = sceneList.getSelectedIndex();
					//remove the item to the left
					String removed = listModel.remove(sel-1);
					listModel.add(sel, removed);
				}
			});
			JButton moveRight = new JButton("-->>");
			moveRight.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					if(sceneList.getSelectedIndex() >= listModel.size()-1)return;
					if(sceneList.getSelectedIndex() < 0)return;
					int sel = sceneList.getSelectedIndex();
					//remove the item to the right
					String removed = listModel.remove(sel+1);
					listModel.add(sel, removed);
				}
			});
			
			JButton delete = new JButton("Delete");
			delete.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					if(sceneList.getSelectedIndex() >= listModel.size())return;
					if(sceneList.getSelectedIndex() < 0)return;
					int sel = sceneList.getSelectedIndex();
					//remove the item to the right
					listModel.remove(sel);
				}
			});
			
			JButton copy = new JButton("Duplicate");
			copy.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					if(sceneList.getSelectedValue() == null)return;
					//if(currentFrame.getImage() == null)return;
					String cln = sceneList.getSelectedValue();
					int sel = sceneList.getSelectedIndex();
					//remove the item to the right
					listModel.add(sel, cln);
				}
			});
			
			JButton rename = new JButton("Rename");
			rename.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					String name = StoryView.this.getName();
					String newName = JOptionPane.showInputDialog((Component) ae.getSource(), "Name:", name);
					if(newName != null) {
						if(newName.contentEquals(name))return;
						if(storyViews.containsKey(newName)) {
							JOptionPane.showMessageDialog((Component) ae.getSource(), "Name already in use");
						}else {
							StoryView.this.rename(newName);
						}
					}
				}
			});
			
			//to do, make setting
			int frameRate = 100;
			JButton play = new JButton("Play");
			play.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					Runnable r = new Runnable() {
						@Override
						public void run() {
							int startIndex = sceneList.getSelectedIndex();
							if(startIndex < 0)startIndex = 0;
							for(int i = startIndex; i < listModel.getSize() + 1; i++) {
								final int c = i == listModel.getSize() ? startIndex : i;
								SwingUtilities.invokeLater(new Runnable() {
									//@Override
									public void run() {
										sceneList.setSelectedIndex(c);
									}
								});
								if(i == listModel.getSize())break;
								String scene = listModel.get(c);
								if(sceneViews.containsKey(scene)) {
									for(int j = 0; j < sceneViews.get(scene).listModel.getSize(); j++) {
										Image image = sceneViews.get(scene).listModel.elementAt(j);
										int index = j;
										SwingUtilities.invokeLater(new Runnable() {
											@Override
											public void run() {
												currentFrame.setImage(image);
												currentFrameLabel.setText(scene + " frame: " + index);
											}
										});
										try {
											Thread.sleep(frameRate);
										} catch (InterruptedException e) {}
									}
								}
							}
						}
					};
					new Thread(r).start();
				}
			});
			
			JPanel control = new JPanel(new FlowLayout(FlowLayout.CENTER));
			control.add(rename);
			control.add(moveLeft);
			control.add(play);
			control.add(copy);
			control.add(delete);
			control.add(moveRight);
			JPanel main = new JPanel(new BorderLayout());
			main.add("Center", currentFrame);
			main.add("South", control);
			main.add("North", currentFrameLabel);
			StoryView.this.getContentPane().add("Center", main);
			JScrollPane sp = new JScrollPane(sceneList);

			StoryView.this.getContentPane().add("South", sp);
			
			StoryView.this.setTitle(storyName);
		}
		
		public void rename(String newName) {
			String currentName = super.getName();
			StoryView.this.setName(newName);
			StoryView.this.setTitle(newName);
			storyViews.put(newName, this);
			storyViews.remove(currentName);
			int currentIndex = stories.getIndexOf(currentName);
			stories.removeElement(currentName);
			stories.insertElementAt(newName, currentIndex);
		}
		
		public int getCursorPosition() {
			return sceneList.getSelectedIndex();
		}
		
		public void addScene(int index, String scene) {
			if(index < 0 || index > listModel.getSize()) {
				index = listModel.getSize();
			}
			listModel.add(index, scene);
			//currentFrame.setImage(image);
			pack();
			repaint();
		}

		public void store(String key, Properties properties) {
			String[] myArray = new String[listModel.getSize()];
			for(int i = 0; i < myArray.length; i++) {
				myArray[i] = listModel.getElementAt(i);
			}
			properties.setProperty(key, String.join(",", myArray));
		}
		
		public void restore(String s, Properties properties) {
			String sceneList = properties.getProperty(s, null);
			if(sceneList != null) {
				String[] sceneArray = sceneList.split(",");
				for(String scn : sceneArray) {
					this.addScene(-1, scn);
				}
			}
		}
		
	}
	
	/**
	 * Returns a string with whitespace and special characters removed, suitable for file names
	 * @param s
	 * @return
	 */
	public String sanitizeString(String s) {
		return s.replaceAll("\\W+", "");
	}
	
	public void addCamera(int port) {
		if(cameraViews.containsKey(port)) {
			logger.info("A camera on port " + port + " already exists");
			return;
		}
		CameraView camera = new CameraView(port);
		desktop.add(camera);
		cameraViews.put(port, camera);
	}
	
	/**
	 * Build the desktop pane with toolbar and add cameras
	 * @param cameraPorts 
	 */
	public void init(Vector<Integer> cameraPorts) {
		mainFrame = new JFrame("Pengaloid");
		mainFrame.setSize(900, 600);
		sceneViews = new TreeMap<String, SceneView>();
		storyViews = new TreeMap<String, StoryView>();
		cameraViews = new TreeMap<Integer, CameraView>();
		
		desktop = new JDesktopPane();
		toolbar = new JToolBar();
		mainFrame.getContentPane().add("Center", desktop);
		mainFrame.getContentPane().add("North", toolbar);
		
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		scenes = new DefaultComboBoxModel<String>();
		stories = new DefaultComboBoxModel<String>();
		
		for(Integer i : cameraPorts) {
			addCamera(i);
		}
		
		JButton newScene = new JButton("New scene...");
		newScene.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				String sceneName = "Scene " + (scenes.getSize()+1);
				addScene(sceneName);
			}
			
		});
		
		JButton newStory = new JButton("New story...");
		newStory.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				String storyName = "Story " + (stories.getSize()+1);
				addStory(storyName);
			}
			
		});
		
		JButton newCamera = new JButton("Add camera...");
		newCamera.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				String portString = JOptionPane.showInputDialog((Component) e.getSource(), "Camera socket:", 50500);
				if(portString != null) {
					try {
						int p = Integer.parseInt(portString);
						addCamera(p);
					} catch(Exception exc) {
						logger.warning(exc.getMessage());
					}
				}
			}
			
		});
		
		JButton setWorkingFolder = new JButton("Set Working Folder");
		
		setWorkingFolder.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				JFileChooser chooser = new JFileChooser();
				chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				int opt = chooser.showSaveDialog((Component) arg0.getSource());
				if(opt == JFileChooser.APPROVE_OPTION) {
					File f = chooser.getSelectedFile();
					if(!f.exists()) {
						f.mkdir();
					}
					workingFolder = f;
					logger.info("Working folder set to " + workingFolder.getAbsolutePath());
				}
				File props = new File(workingFolder, "project.properties");
				if(props.exists()) {
					restore();
				};
			}
			
		});
		
		JButton openProject = new JButton("Load Project");
		
		openProject.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				JFileChooser chooser = new JFileChooser();
				chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				int opt = chooser.showOpenDialog((Component) arg0.getSource());
				if(opt == JFileChooser.APPROVE_OPTION) {
					File f = chooser.getSelectedFile();
					if(!f.exists()) {
						f.mkdir();
					}
					workingFolder = f;
					logger.info("Working folder set to " + workingFolder.getAbsolutePath());
				}
				File props = new File(workingFolder, "project.properties");
				if(props.exists()) {
					restore();
				};
			}
			
		});
		
		JButton save = new JButton("Save");
		save.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				if(workingFolder == null) {
					logger.warning("Working folder not set");
					return;
				}
				if(!workingFolder.exists()) {
					if(!workingFolder.mkdir()) {
						logger.warning("Failed to create working folder");
						return;
					}
				}
				store();
			}
			
		});
		
		JButton deleteScene = new JButton("Delete Scene");
		deleteScene.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				JList<String> toDelete = new JList(scenes);
				toDelete.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
				JScrollPane sp = new JScrollPane(toDelete);
				JPanel panel = new JPanel(new BorderLayout());
				panel.add("Center", sp);
				panel.add("North", new JLabel("Select scenes to delete"));
				int result = JOptionPane.showConfirmDialog((Component) e.getSource(), panel);
				if(result == JOptionPane.OK_OPTION) {
					List<String> items = toDelete.getSelectedValuesList();
					for(String s : items) {
						deleteScene(s);
					}
				}
			}
			
		});
		
		JButton deleteStory = new JButton("Delete Story");
		deleteStory.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				JList<String> toDelete = new JList(stories);
				toDelete.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
				JScrollPane sp = new JScrollPane(toDelete);
				JPanel panel = new JPanel(new BorderLayout());
				panel.add("Center", sp);
				panel.add("North", new JLabel("Select stories to delete"));
				int result = JOptionPane.showConfirmDialog((Component) e.getSource(), panel);
				if(result == JOptionPane.OK_OPTION) {
					List<String> items = toDelete.getSelectedValuesList();
					for(String s : items) {
						deleteStory(s);
					}
				}
			}
			
		});
		
		toolbar.add(setWorkingFolder);
		toolbar.add(openProject);
		toolbar.add(save);
		toolbar.add(newCamera);
		toolbar.add(newScene);
		toolbar.add(newStory);
		toolbar.add(deleteScene);
		toolbar.add(deleteStory);
		
	}
	
	SceneView addScene(String sceneName) {
		SceneView view = new SceneView(sceneName);
		sceneViews.put(sceneName, view);
		scenes.addElement(sceneName);
		view.setVisible(true);
		view.setSize(new Dimension(300, 300));
		desktop.add(view);
		return view;
	}
	
	void deleteScene(String sceneName) {
		SceneView view = sceneViews.get(sceneName);
		//need to remove any references to this scene from any stories
		for(StoryView sv : storyViews.values()) {
			do {
				
			} while(sv.listModel.removeElement(sceneName));
		}
		if(view == null) {
			logger.warning(sceneName + " does not exist");
		}
		scenes.removeElement(sceneName);
		desktop.remove(view);
		sceneViews.remove(sceneName);
		view.dispose();
		desktop.repaint();
	}
	
	StoryView addStory(String storyName) {
		StoryView view = new StoryView(storyName);
		storyViews.put(storyName, view);
		stories.addElement(storyName);
		view.setVisible(true);
		view.setSize(new Dimension(300, 300));
		desktop.add(view);
		return view;
	}
	
	void deleteStory(String storyName) {
		StoryView view = storyViews.get(storyName);
		if(view == null) {
			logger.warning(storyName + " does not exist");
		}
		stories.removeElement(storyName);
		desktop.remove(view);
		storyViews.remove(storyName);
		view.dispose();
		desktop.repaint();
	}
	
	/**
	 * Converts a given Image into a BufferedImage
	 *
	 * @param img The Image to be converted
	 * @return The converted BufferedImage
	 */
	public static BufferedImage toBufferedImage(Image img)
	{
	    if (img instanceof BufferedImage)
	    {
	        return (BufferedImage) img;
	    }

	    // Create a buffered image with transparency
	    BufferedImage bimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

	    // Draw the image on to the buffered image
	    Graphics2D bGr = bimage.createGraphics();
	    bGr.drawImage(img, 0, 0, null);
	    bGr.dispose();

	    // Return the buffered image
	    return bimage;
	}
	
	public void storeImage(File f, Image image) {
		try {
			if(!f.exists()) {
				f.createNewFile();
			}
		    ImageIO.write(toBufferedImage(image), "png", f);
		} catch (IOException e) {
		    logger.warning(e.getMessage());
		}
	}
	
	private void clean() throws IOException {
		if(workingFolder != null && workingFolder.exists()) {
			File project = new File(workingFolder, "project.properties");
			InputStream input;
			input = new FileInputStream(project);
			Properties properties = new Properties();
			properties.load(input);
			String sceneList = properties.getProperty("scenes", null);
			if(sceneList != null) {
				String[] sceneNames = sceneList.split(",");
				for(String s : sceneNames) {
					String dirName = sanitizeString(s);
					File sceneDir = new File(workingFolder, dirName);
					if(sceneDir.exists()) {
						File[] files = sceneDir.listFiles();
						for(File f : files) {
							if(f.getName().endsWith(".png")) {
								logger.fine("Deleting " + f.getAbsolutePath());
								f.delete();
							}
						}
						logger.info("Deleting " + sceneDir.getAbsolutePath());
						sceneDir.delete();
					}
				}
			}
			project.delete();
		}
	}
	
	public void store() {

		try {
			
			try {
				clean();
			} catch(IOException e) {
				logger.warning("Failed to clean working folder: " + e.getMessage());
			}
			
			Properties properties = new Properties();

			File project = new File(workingFolder, "project.properties");
			OutputStream output = new FileOutputStream(project);
			String sceneList = String.join(",", sceneViews.keySet());
			String storyList = String.join(",", storyViews.keySet());
			properties.setProperty("scenes", sceneList);
			properties.setProperty("stories", storyList);
			for(Map.Entry<String, SceneView> entry : sceneViews.entrySet()) {
				entry.getValue().store(entry.getKey(), properties);
			}
			for(Map.Entry<String, StoryView> entry : storyViews.entrySet()) {
				entry.getValue().store(entry.getKey(), properties);
			}
			properties.store(output, new Date().toString());
		} catch (IOException e) {
			logger.warning("Unable to save project: " + e.getMessage());
		}

	}
	
	public void restore() {

		try {
			Properties properties = new Properties();
			File project = new File(workingFolder, "project.properties");
			InputStream input;
			input = new FileInputStream(project);
			properties.load(input);
			String sceneList = properties.getProperty("scenes", null);
			String storyList = properties.getProperty("stories", null);
			if(sceneList != null) {
				String[] sceneNames = sceneList.split(",");
				for(String s : sceneNames) {
					SceneView view = addScene(s);
					view.restore(s, properties);
				}
			}
			if(storyList != null) {
				String[] storyNames = storyList.split(",");
				for(String s : storyNames) {
					StoryView view = addStory(s);
					view.restore(s, properties);
				}
			}
		} catch (FileNotFoundException e) {
			logger.warning(e.getMessage());
		} catch (IOException e) {
			logger.warning(e.getMessage());
		}
		
	}
	
	public void showGUI() {
		mainFrame.setVisible(true);
	}
	
	/**
	 * Launch the application, CameraViews will be created on supplied ports for any integer arguments
	 * @param args
	 */
	public static void main(String[] args) {
		LookAndFeel laf = new NimbusLookAndFeel();
		try {
			UIManager.setLookAndFeel(laf);
		} catch (UnsupportedLookAndFeelException e) {
			System.out.println("Unable to set look and feel: " + e.getMessage());
		}
		WorkSurface app = new WorkSurface();
		
		//make a camera for each supplied port
		Vector<Integer> cameraPorts = new Vector<Integer>();
		for(String s : args) {
			try {
				int p = Integer.parseInt(s);
				cameraPorts.add(p);
			} catch (Exception e) {
				
			}
		}
		
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				app.init(cameraPorts);
				
				app.showGUI();
			}
			
		});
		
	}

}
